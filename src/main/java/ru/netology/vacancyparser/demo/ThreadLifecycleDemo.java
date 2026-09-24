package ru.netology.vacancyparser.demo;

public class ThreadLifecycleDemo {

    public static void main(String[] args) throws InterruptedException {
        Object dbLock = new Object();

        // Поток 1: захватывает блокировку, работает, засыпает, не отпуская ее
        Thread vacancySaver = new Thread(() -> {
            synchronized (dbLock) {
                busyWork(300);
                sleepQuietly(2000);
            }
        }, "VacancySaver");

        // Поток 2: хочет ту же блокировку и стоит, пока она занята
        Thread vacancyReader = new Thread(() -> {
            synchronized (dbLock) {   // BLOCKED, пока dbLock держит VacancySaver
                busyWork(300);        // RUNNABLE
            }
        }, "VacancyReader");

        // Поток 3: ждет завершения VacancySaver через join()
        Thread reportBuilder = new Thread(() -> {
            try {
                vacancySaver.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            busyWork(300);            // RUNNABLE
        }, "ReportBuilder");

        Thread[] threads = {vacancySaver, vacancyReader, reportBuilder};
        Thread.State[] lastStates = new Thread.State[threads.length];
        long startTime = System.currentTimeMillis();

        logChanges(threads, lastStates, startTime);

        vacancySaver.start();
        Thread.sleep(100);
        vacancyReader.start();
        reportBuilder.start();

        // Главный поток: каждые 20 мс проверяет состояния
        while (!allTerminated(threads)) {
            logChanges(threads, lastStates, startTime);
            Thread.sleep(20);
        }
        logChanges(threads, lastStates, startTime);
    }

    // Печатает строку только если состояние потока изменилось
    private static void logChanges(Thread[] threads, Thread.State[] lastStates, long startTime) {
        for (int i = 0; i < threads.length; i++) {
            Thread.State current = threads[i].getState();
            if (current != lastStates[i]) {
                long elapsed = System.currentTimeMillis() - startTime;
                String name = threads[i].getName();
                if (lastStates[i] == null) {
                    System.out.printf("%5d мс  %-14s %s%n", elapsed, name, current);
                } else {
                    System.out.printf("%5d мс  %-14s %s -> %s%n", elapsed, name, lastStates[i], current);
                }
                lastStates[i] = current;
            }
        }
    }

    private static boolean allTerminated(Thread[] threads) {
        for (Thread t : threads) {
            if (t.getState() != Thread.State.TERMINATED) {
                return false;
            }
        }
        return true;
    }

    private static void busyWork(long millis) {
        long end = System.nanoTime() + millis * 1_000_000;
        while (System.nanoTime() < end) {
        }
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}