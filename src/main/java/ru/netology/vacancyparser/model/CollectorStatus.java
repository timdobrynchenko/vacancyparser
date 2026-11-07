package ru.netology.vacancyparser.model;

import java.util.Map;

// Ответ GET /api/status: что сейчас в базе и как работает пул
public record CollectorStatus(
        long vacanciesInDatabase,
        Map<ParseStatus, Long> tasksByStatus, // сколько задач в каждом статусе
        long runs,              // сколько запусков сбора было с момента старта
        int inProgressNow,      // сколько вакансий грузится прямо сейчас
        int poolSize,
        int activeThreads,      // сколько потоков пула сейчас заняты
        int queuedTasks,        // сколько задач ждут в очереди пула
        int freeHhPermits,      // сколько еще запросов к hh.ru можно начать прямо сейчас
        CollectReport lastRun
) {
}
