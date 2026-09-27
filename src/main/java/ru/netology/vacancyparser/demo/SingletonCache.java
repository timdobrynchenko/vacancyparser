package ru.netology.vacancyparser.demo;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class SingletonCache {

    private final AtomicReference<String> value = new AtomicReference<>();
    private final AtomicInteger createdCount = new AtomicInteger();

    public String getOrCreate() {
        String current = value.get();
        if (current != null) {
            return current;
        }
        String created = createExpensiveValue();
        if (value.compareAndSet(null, created)) {
            return created;
        }
        return value.get();
    }

    public String peek() {
        return value.get();
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