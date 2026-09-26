package ru.netology.vacancyparser.demo;

// Счетчик без синхронизации
public class UnsafeDataCollector {

    private volatile int processedCount = 0;

    public void incrementProcessed() {
        processedCount++;
    }

    public int getProcessedCount() {
        return processedCount;
    }
}