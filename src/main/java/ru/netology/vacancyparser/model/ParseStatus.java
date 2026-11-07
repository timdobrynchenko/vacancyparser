package ru.netology.vacancyparser.model;

// Статус задачи на загрузку одной вакансии
public enum ParseStatus {
    NEW,          // задача создана, ждет свободный поток
    IN_PROGRESS,  // поток пула загружает страницу
    DONE,         // вакансия сохранена
    ERROR,        // страница не загрузилась или не разобралась
    CANCELLED     // не успела за таймаут и была отменена
}
