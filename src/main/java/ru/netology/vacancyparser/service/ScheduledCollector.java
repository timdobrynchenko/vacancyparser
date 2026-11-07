package ru.netology.vacancyparser.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ru.netology.vacancyparser.model.CollectReport;

// Сбор по расписанию: через 5 секунд после старта, потом раз в 30 минут.
// В тесте выключен через collector.schedule.enabled=false.
@Component
@ConditionalOnProperty(name = "collector.schedule.enabled", havingValue = "true")
public class ScheduledCollector {

    private static final Logger log = LoggerFactory.getLogger(ScheduledCollector.class);

    private final VacancyCollectorService collector;
    private final String query;
    private final int area;
    private final int limit;

    public ScheduledCollector(VacancyCollectorService collector,
                              @Value("${collector.schedule.query}") String query,
                              @Value("${collector.schedule.area}") int area,
                              @Value("${collector.schedule.limit}") int limit) {
        this.collector = collector;
        this.query = query;
        this.area = area;
        this.limit = limit;
    }

    // fixedDelay считает от конца прошлого запуска, поэтому запуски не накладываются
    @Scheduled(initialDelayString = "${collector.schedule.initial-delay-ms}",
               fixedDelayString = "${collector.schedule.delay-ms}")
    public void collect() {
        log.info("[{}] сбор по расписанию: {}", Thread.currentThread().getName(), query);
        CollectReport report = collector.collectBySearch(query, area, limit, "расписание");
        log.info("Расписание: сохранено {} из {}", report.saved(), report.requested());
    }
}
