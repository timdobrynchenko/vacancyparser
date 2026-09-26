package ru.netology.vacancyparser.demo;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class DataCollector {

    private final int id;
    private final String name;
    private final List<VacancyItem> items = new ArrayList<>();
    private final Set<String> processedKeys = new HashSet<>();
    private int processedCount = 0;

    public DataCollector(int id, String name) {
        this.id = id;
        this.name = name;
    }

    // --- Методы из задания ---

    public synchronized void collectItem(VacancyItem item) {
        items.add(item);
        processedKeys.add(item.id());
    }

    public synchronized boolean isAlreadyProcessed(String key) {
        return processedKeys.contains(key);
    }

    public synchronized void incrementProcessed() {
        processedCount++;
        notifyAll();
    }

    // --- Проверка и добавление одним действие ---

    public synchronized boolean collectIfNew(VacancyItem item) {
        if (processedKeys.contains(item.id())) {
            return false;
        }
        collectItem(item);
        return true;
    }

    // --- Ожидание ---

    public synchronized void awaitProcessed(int target) throws InterruptedException {
        while (processedCount < target) {
            wait();
        }
    }

    public synchronized int getProcessedCount() {
        return processedCount;
    }

    public synchronized int getItemCount() {
        return items.size();
    }

    public String getName() {
        return name;
    }

    private synchronized VacancyItem removeLast() {
        if (items.isEmpty()) {
            return null;
        }
        VacancyItem item = items.remove(items.size() - 1);
        processedKeys.remove(item.id());
        return item;
    }

    // --- Перенос вакансии между двумя коллекторами ---

    // Два потока с разными направлениями могут захватить по замку и ждать друг друга вечно
    public static void transferWithDeadlockRisk(DataCollector from, DataCollector to) {
        synchronized (from) {
            synchronized (to) {
                moveOne(from, to);
            }
        }
    }

    // Безопасный вариант: всегда сначала коллектор с меньшим id
    public static void transferSafe(DataCollector from, DataCollector to) {
        DataCollector first;
        DataCollector second;
        if (from.id < to.id) {
            first = from;
            second = to;
        } else {
            first = to;
            second = from;
        }
        synchronized (first) {
            synchronized (second) {
                moveOne(from, to);
            }
        }
    }

    private static void moveOne(DataCollector from, DataCollector to) {
        VacancyItem item = from.removeLast();
        if (item != null) {
            to.collectItem(item);
        }
    }
}