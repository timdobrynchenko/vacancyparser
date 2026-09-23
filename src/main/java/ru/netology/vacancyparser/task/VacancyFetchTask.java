package ru.netology.vacancyparser.task;

public class VacancyFetchTask implements Runnable {

    // Поля
    private final int number;
    private final String sourceUrl;

    // Конструктор: вызывается при new VacancyFetchTask(1, "...").
    public VacancyFetchTask(int number, String sourceUrl) {
        this.number = number;
        this.sourceUrl = sourceUrl;
    }

    @Override
    public void run() {
        String threadName = Thread.currentThread().getName();
        System.out.println("[" + threadName + "] задача №" + number + " -> " + sourceUrl);
    }
}
