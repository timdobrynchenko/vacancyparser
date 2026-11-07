package ru.netology.vacancyparser.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import ru.netology.vacancyparser.model.AnswerPage;
import ru.netology.vacancyparser.model.CollectReport;
import ru.netology.vacancyparser.model.CollectRequest;
import ru.netology.vacancyparser.model.CollectorStatus;
import ru.netology.vacancyparser.model.ParseStatus;
import ru.netology.vacancyparser.model.ParseTask;
import ru.netology.vacancyparser.model.SearchRequest;
import ru.netology.vacancyparser.model.Vacancy;
import ru.netology.vacancyparser.repository.ParseTaskRepository;
import ru.netology.vacancyparser.repository.VacancyRepository;
import ru.netology.vacancyparser.service.VacancyCollectorService;
import ru.netology.vacancyparser.service.VacancyQueryService;

import java.util.List;

// Принимает запросы и передает работу сервисам
@RestController
public class VacancyController {

    private final VacancyCollectorService collector;
    private final VacancyQueryService queryService;
    private final VacancyRepository repository;
    private final ParseTaskRepository taskRepository;

    public VacancyController(VacancyCollectorService collector,
                             VacancyQueryService queryService,
                             VacancyRepository repository,
                             ParseTaskRepository taskRepository) {
        this.collector = collector;
        this.queryService = queryService;
        this.repository = repository;
        this.taskRepository = taskRepository;
    }

    // Собрать вакансии по ссылкам: {"urls": ["https://hh.ru/vacancy/136231014"]}
    @PostMapping("/api/vacancies/collect")
    public CollectReport collect(@RequestBody CollectRequest request) {
        if (request.urls() == null || request.urls().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "нужен непустой список urls");
        }
        if (request.urls().size() > 50) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "не больше 50 ссылок за раз");
        }
        return collector.collectByUrls(request.urls(), "запрос");
    }

    // Собрать первые limit вакансий из поиска hh.ru: {"text": "java", "area": 2, "limit": 10}
    @PostMapping("/api/vacancies/search")
    public CollectReport search(@RequestBody SearchRequest request) {
        if (request.text() == null || request.text().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "нужен текст поиска text");
        }
        int area = 113; // 113 - вся Россия
        if (request.area() != null) {
            area = request.area();
        }
        int limit = 10;
        if (request.limit() != null) {
            limit = Math.max(1, Math.min(request.limit(), 50));
        }
        return collector.collectBySearch(request.text(), area, limit, "запрос");
    }

    // Результаты из H2: /answer?city=Санкт-Петербург&minSalary=150000&sort=salary&order=desc&page=0&size=5
    @GetMapping("/answer")
    public AnswerPage answer(@RequestParam(required = false) String city,
                             @RequestParam(required = false) String company,
                             @RequestParam(required = false) Integer minSalary,
                             @RequestParam(required = false) String text,
                             @RequestParam(defaultValue = "date") String sort,
                             @RequestParam(defaultValue = "desc") String order,
                             @RequestParam(defaultValue = "0") int page,
                             @RequestParam(defaultValue = "10") int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "page >= 0, size от 1 до 100");
        }
        return queryService.answer(city, company, minSalary, text, sort, order, page, size);
    }

    @GetMapping("/answer/{id}")
    public Vacancy one(@PathVariable String id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "вакансии " + id + " нет в базе"));
    }

    // Последние 50 задач, можно отфильтровать по статусу: /api/tasks?status=ERROR
    @GetMapping("/api/tasks")
    public List<ParseTask> tasks(@RequestParam(required = false) ParseStatus status) {
        if (status == null) {
            return taskRepository.findTop50ByOrderByIdDesc();
        }
        return taskRepository.findTop50ByStatusOrderByIdDesc(status);
    }

    @GetMapping("/api/status")
    public CollectorStatus status() {
        return collector.status();
    }
}
