package ru.netology.vacancyparser.model;

import java.util.List;

// Ответ GET /answer: одна страница отфильтрованных и отсортированных вакансий
public record AnswerPage(
        long total,     // сколько вакансий подошло под фильтр
        int page,
        int size,
        String sort,
        String order,
        List<Vacancy> items
) {
}
