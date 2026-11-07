package ru.netology.vacancyparser.task;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.netology.vacancyparser.client.HhClient;
import ru.netology.vacancyparser.client.HhPageParser;
import ru.netology.vacancyparser.client.SampleVacancies;
import ru.netology.vacancyparser.model.ParseStatus;
import ru.netology.vacancyparser.model.ParseTask;
import ru.netology.vacancyparser.model.Vacancy;
import ru.netology.vacancyparser.repository.ParseTaskRepository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;

// Загрузка одной вакансии в потоке пула.
// Callable, потому что задача возвращает результат - готовую Vacancy.
public class VacancyLoadTask implements Callable<Vacancy> {

    private static final Logger log = LoggerFactory.getLogger(VacancyLoadTask.class);

    private final ParseTask parseTask;
    private final HhClient hhClient;
    private final HhPageParser parser;
    private final SampleVacancies samples;
    private final ParseTaskRepository taskRepository;
    // общая для всех задач одного запуска: какой поток сколько вакансий загрузил
    private final ConcurrentHashMap<String, Integer> loadedByThread;

    public VacancyLoadTask(ParseTask parseTask,
                           HhClient hhClient,
                           HhPageParser parser,
                           SampleVacancies samples,
                           ParseTaskRepository taskRepository,
                           ConcurrentHashMap<String, Integer> loadedByThread) {
        this.parseTask = parseTask;
        this.hhClient = hhClient;
        this.parser = parser;
        this.samples = samples;
        this.taskRepository = taskRepository;
        this.loadedByThread = loadedByThread;
    }

    @Override
    public Vacancy call() throws Exception {
        String worker = Thread.currentThread().getName();
        String vacancyId = parseTask.getVacancyId();

        parseTask.setStatus(ParseStatus.IN_PROGRESS);
        parseTask.setWorker(worker);
        taskRepository.save(parseTask);
        log.info("[{}] задача #{} (вакансия {}): {}", worker, parseTask.getId(), vacancyId, ParseStatus.IN_PROGRESS);

        Vacancy vacancy = load(vacancyId);
        vacancy.setCollectedBy(worker);
        vacancy.setCollectedAt(LocalDateTime.now());
        loadedByThread.merge(worker, 1, Integer::sum);
        return vacancy;
    }

    private Vacancy load(String vacancyId) throws Exception {
        // вакансии-примеры есть только локально, на hh.ru за ними не ходим
        Optional<Vacancy> sample = samples.findById(vacancyId);
        if (sample.isPresent()) {
            return sample.get();
        }
        String html = hhClient.loadVacancyPage(vacancyId);
        return parser.parseVacancy(vacancyId, html);
    }

    public ParseTask getParseTask() {
        return parseTask;
    }
}
