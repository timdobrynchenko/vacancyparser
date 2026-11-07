package ru.netology.vacancyparser.service;

import org.springframework.stereotype.Service;
import ru.netology.vacancyparser.model.AnswerPage;
import ru.netology.vacancyparser.model.Vacancy;
import ru.netology.vacancyparser.repository.VacancyRepository;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

// Фильтр, сортировка и страницы для /answer.
// По заданию делаем это через parallelStream, а не SQL-запросом.
@Service
public class VacancyQueryService {

    private final VacancyRepository repository;

    public VacancyQueryService(VacancyRepository repository) {
        this.repository = repository;
    }

    public AnswerPage answer(String city, String company, Integer minSalary, String text,
                             String sort, String order, int page, int size) {
        List<Vacancy> all = repository.findAll();

        Predicate<Vacancy> filter = vacancy -> contains(vacancy.getCity(), city)
                && contains(vacancy.getCompany(), company)
                && salaryAtLeast(vacancy, minSalary)
                && (contains(vacancy.getTitle(), text) || contains(vacancy.getSkills(), text));

        boolean desc = "desc".equalsIgnoreCase(order);

        long total = all.parallelStream().filter(filter).count();

        List<Vacancy> items = all.parallelStream()
                .filter(filter)
                .sorted(comparator(sort, desc))
                .skip((long) page * size)
                .limit(size)
                .toList();

        return new AnswerPage(total, page, size, sort, order, items);
    }

    // Вакансии без значения (например, без зарплаты) всегда идут в конце
    private Comparator<Vacancy> comparator(String sort, boolean desc) {
        if ("salary".equalsIgnoreCase(sort)) {
            Comparator<Integer> bySalary = Comparator.naturalOrder();
            if (desc) {
                bySalary = bySalary.reversed();
            }
            return Comparator.comparing(Vacancy::salaryForSort, Comparator.nullsLast(bySalary));
        }
        if ("title".equalsIgnoreCase(sort) || "company".equalsIgnoreCase(sort)) {
            Comparator<String> byText = String.CASE_INSENSITIVE_ORDER;
            if (desc) {
                byText = byText.reversed();
            }
            if ("title".equalsIgnoreCase(sort)) {
                return Comparator.comparing(Vacancy::getTitle, Comparator.nullsLast(byText));
            }
            return Comparator.comparing(Vacancy::getCompany, Comparator.nullsLast(byText));
        }
        Comparator<LocalDateTime> byDate = Comparator.naturalOrder();
        if (desc) {
            byDate = byDate.reversed();
        }
        return Comparator.comparing(Vacancy::getPublishedAt, Comparator.nullsLast(byDate));
    }

    private boolean contains(String value, String part) {
        if (part == null || part.isBlank()) {
            return true;
        }
        if (value == null) {
            return false;
        }
        return value.toLowerCase().contains(part.toLowerCase());
    }

    // Сравниваем с верхней границей: вакансия до 200 000 подходит под minSalary=150000
    private boolean salaryAtLeast(Vacancy vacancy, Integer minSalary) {
        if (minSalary == null) {
            return true;
        }
        Integer salary = vacancy.getSalaryTo();
        if (salary == null) {
            salary = vacancy.getSalaryFrom();
        }
        if (salary == null) {
            return false;
        }
        return salary >= minSalary;
    }
}
