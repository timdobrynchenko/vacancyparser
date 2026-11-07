package ru.netology.vacancyparser.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import ru.netology.vacancyparser.client.HhClient;
import ru.netology.vacancyparser.client.HhPageParser;
import ru.netology.vacancyparser.client.SampleVacancies;
import ru.netology.vacancyparser.model.CollectReport;
import ru.netology.vacancyparser.model.CollectorStatus;
import ru.netology.vacancyparser.model.ParseStatus;
import ru.netology.vacancyparser.model.ParseTask;
import ru.netology.vacancyparser.model.Vacancy;
import ru.netology.vacancyparser.repository.ParseTaskRepository;
import ru.netology.vacancyparser.repository.VacancyRepository;
import ru.netology.vacancyparser.task.VacancyLoadTask;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

// Раздает вакансии потокам пула, собирает результаты и сохраняет в H2.
// Вызывается и из контроллера, и из планировщика, поэтому общие поля потокобезопасные.
@Service
public class VacancyCollectorService {

    private static final Logger log = LoggerFactory.getLogger(VacancyCollectorService.class);

    private final ThreadPoolExecutor executor;
    private final HhClient hhClient;
    private final HhPageParser parser;
    private final SampleVacancies samples;
    private final VacancyRepository repository;
    private final ParseTaskRepository taskRepository;
    private final boolean fallbackToSample;
    private final long taskTimeoutSeconds;

    // вакансии, которые грузятся прямо сейчас, чтобы два запуска не грузили одну и ту же
    private final Set<String> inProgress = ConcurrentHashMap.newKeySet();
    private final AtomicLong runs = new AtomicLong();
    private final AtomicReference<CollectReport> lastReport = new AtomicReference<>();

    public VacancyCollectorService(ThreadPoolExecutor executor,
                                   HhClient hhClient,
                                   HhPageParser parser,
                                   SampleVacancies samples,
                                   VacancyRepository repository,
                                   ParseTaskRepository taskRepository,
                                   @Value("${hh.fallback-to-sample}") boolean fallbackToSample,
                                   @Value("${collector.task-timeout-seconds}") long taskTimeoutSeconds) {
        this.executor = executor;
        this.hhClient = hhClient;
        this.parser = parser;
        this.samples = samples;
        this.repository = repository;
        this.taskRepository = taskRepository;
        this.fallbackToSample = fallbackToSample;
        this.taskTimeoutSeconds = taskTimeoutSeconds;
    }

    public CollectReport collectByUrls(List<String> urls, String trigger) {
        runs.incrementAndGet();
        List<String> ids = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        for (String url : urls) {
            Optional<String> id = parser.extractId(url);
            if (id.isPresent()) {
                ids.add(id.get());
            } else {
                errors.add("не похоже на ссылку на вакансию hh.ru: " + url);
            }
        }
        return collect(ids, errors, trigger, "hh.ru");
    }

    public CollectReport collectBySearch(String text, int area, int limit, String trigger) {
        runs.incrementAndGet();
        List<String> errors = new ArrayList<>();
        List<String> ids = new ArrayList<>();
        try {
            String html = hhClient.loadSearchPage(text, area);
            for (String id : parser.parseSearchIds(html)) {
                if (ids.size() >= limit) {
                    break;
                }
                ids.add(id);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            errors.add("поиск прерван");
        } catch (Exception e) {
            errors.add("поиск на hh.ru не удался: " + e.getMessage());
        }

        CollectReport report = collect(ids, errors, trigger, "hh.ru");

        // hh.ru недоступен или показал капчу и ничего не сохранилось: берем встроенные примеры
        if (report.saved() == 0 && fallbackToSample) {
            List<String> sampleErrors = new ArrayList<>(report.errors());
            sampleErrors.add("с hh.ru ничего не загрузилось, взяты встроенные примеры");
            report = collect(samples.searchIds(text, limit), sampleErrors, trigger, "пример (hh.ru недоступен)");
        }
        return report;
    }

    private CollectReport collect(List<String> rawIds, List<String> errors, String trigger, String source) {
        long start = System.currentTimeMillis();
        Set<String> ids = new LinkedHashSet<>(rawIds);
        ConcurrentHashMap<String, Integer> loadedByThread = new ConcurrentHashMap<>();

        // 1. Для каждой вакансии создаем задачу со статусом NEW
        List<ParseTask> parseTasks = new ArrayList<>();
        int skipped = 0;
        for (String id : ids) {
            // add вернет false, если эту вакансию уже грузит другой запуск
            if (!inProgress.add(id)) {
                skipped++;
                continue;
            }
            String url = null;
            if ("hh.ru".equals(source)) {
                url = "https://hh.ru/vacancy/" + id;
            }
            parseTasks.add(new ParseTask(id, url, trigger));
        }
        taskRepository.saveAll(parseTasks);

        List<VacancyLoadTask> tasks = new ArrayList<>();
        for (ParseTask parseTask : parseTasks) {
            tasks.add(new VacancyLoadTask(parseTask, hhClient, parser, samples, taskRepository, loadedByThread));
        }

        // 2. invokeAll отдает все задачи пулу и ждет их, но не дольше таймаута.
        // Задачи, которые не успели, пул отменяет сам.
        List<Vacancy> loaded = new ArrayList<>();
        try {
            List<Future<Vacancy>> futures = executor.invokeAll(tasks, taskTimeoutSeconds, TimeUnit.SECONDS);

            // 3. Разбираем результаты и ставим итоговый статус
            for (int i = 0; i < futures.size(); i++) {
                Future<Vacancy> future = futures.get(i);
                ParseTask parseTask = parseTasks.get(i);
                if (future.isCancelled()) {
                    finish(parseTask, ParseStatus.CANCELLED, "не успела за " + taskTimeoutSeconds + " с", errors);
                    continue;
                }
                try {
                    loaded.add(future.get());
                    finish(parseTask, ParseStatus.DONE, null, errors);
                } catch (ExecutionException e) {
                    finish(parseTask, ParseStatus.ERROR, e.getCause().getMessage(), errors);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            errors.add("сбор прерван");
        } finally {
            for (ParseTask parseTask : parseTasks) {
                inProgress.remove(parseTask.getVacancyId());
            }
        }

        repository.saveAll(loaded);
        taskRepository.saveAll(parseTasks);

        CollectReport report = new CollectReport(
                trigger,
                source,
                ids.size(),
                loaded.size(),
                skipped,
                errors,
                Map.copyOf(loadedByThread),
                System.currentTimeMillis() - start,
                LocalDateTime.now());
        lastReport.set(report);
        log.info("Сбор ({}) завершен: запрошено {}, сохранено {}, ошибок {}, потоки {}",
                trigger, report.requested(), report.saved(), errors.size(), report.loadedByThread());
        return report;
    }

    private void finish(ParseTask parseTask, ParseStatus status, String error, List<String> errors) {
        if (error != null && error.length() > 500) {
            error = error.substring(0, 500);
        }
        parseTask.setStatus(status);
        parseTask.setError(error);
        parseTask.setFinishedAt(LocalDateTime.now());
        if (error != null) {
            errors.add(parseTask.getVacancyId() + ": " + error);
        }
        log.info("задача #{} (вакансия {}): {}", parseTask.getId(), parseTask.getVacancyId(), status);
    }

    public CollectorStatus status() {
        Map<ParseStatus, Long> tasksByStatus = new LinkedHashMap<>();
        for (ParseStatus status : ParseStatus.values()) {
            tasksByStatus.put(status, taskRepository.countByStatus(status));
        }
        return new CollectorStatus(
                repository.count(),
                tasksByStatus,
                runs.get(),
                inProgress.size(),
                executor.getPoolSize(),
                executor.getActiveCount(),
                executor.getQueue().size(),
                hhClient.availablePermits(),
                lastReport.get());
    }
}
