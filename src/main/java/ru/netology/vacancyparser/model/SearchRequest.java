package ru.netology.vacancyparser.model;

// Тело запроса POST /api/vacancies/search: {"text": "java", "area": 2, "limit": 10}
// area - регион hh.ru: 1 - Москва, 2 - Санкт-Петербург, 113 - вся Россия
public record SearchRequest(String text, Integer area, Integer limit) {
}
