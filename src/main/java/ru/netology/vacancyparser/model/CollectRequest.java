package ru.netology.vacancyparser.model;

import java.util.List;

// Тело запроса POST /api/vacancies/collect: {"urls": ["https://hh.ru/vacancy/136231014", ...]}
public record CollectRequest(List<String> urls) {
}
