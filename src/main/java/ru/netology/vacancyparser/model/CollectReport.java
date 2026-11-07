package ru.netology.vacancyparser.model;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

// Итог одного запуска сбора - его возвращают POST-эндпоинты и хранит статус.
public record CollectReport(
        String trigger,                     // кто запустил: запрос по ссылкам, поиск или расписание
        String source,                      // откуда брали вакансии: hh.ru или встроенный пример
        int requested,                      // сколько вакансий пытались загрузить
        int saved,                          // сколько сохранили в базу
        int skipped,                        // сколько пропустили: дубли и те, что уже грузит другой запуск
        List<String> errors,                // что не получилось и почему
        Map<String, Integer> loadedByThread, // какой поток пула сколько вакансий загрузил
        long durationMs,
        LocalDateTime finishedAt
) {
}
