package ru.netology.vacancyparser.client;

// Страница загрузилась, но вакансии в ней нет: капча, архивная вакансия или hh.ru поменял разметку
public class VacancyParseException extends RuntimeException {

    public VacancyParseException(String message) {
        super(message);
    }
}
