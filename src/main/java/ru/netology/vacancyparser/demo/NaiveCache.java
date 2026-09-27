package ru.netology.vacancyparser.demo;

import java.util.concurrent.atomic.AtomicInteger;

public class NaiveCache {

    private String value;
    private final AtomicInteger createdCount = new AtomicInteger();

    public String getOrCreate() {
        if (value == null) {
            value = createExpensiveValue();
        }
        return value;
    }

    public int getCreatedCount() {
        return createdCount.get();
    }

    private String createExpensiveValue() {
        int number = createdCount.incrementAndGet();
        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return new String("config-" + number);
    }
}