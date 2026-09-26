package ru.netology.vacancyparser.demo;

import java.util.ArrayList;
import java.util.List;

public class SyncDemo {

    private static final int THREADS = 8;

    public static void main(String[] args) throws InterruptedException {
        raceConditionTest();
        duplicateTest();
        waitNotifyTest();
        deadlockTest();
    }

    private static void raceConditionTest() throws InterruptedException {
        System.out.println("=== 1. Гонка данных на счетчике ===");
        int incrementsPerThread = 1_000_000;
        int expected = THREADS * incrementsPerThread;

        UnsafeDataCollector unsafe = new UnsafeDataCollector();
        long unsafeTime = runInThreads(() -> {
            for (int i = 0; i < incrementsPerThread; i++) {
                unsafe.incrementProcessed();
            }
        });

        DataCollector safe = new DataCollector(0, "safe");
        long safeTime = runInThreads(() -> {
            for (int i = 0; i < incrementsPerThread; i++) {
                safe.incrementProcessed();
            }
        });

        System.out.printf("Ожидалось:         %,d%n", expected);
        System.out.printf("Без synchronized:  %,d  (потеряно %,d)  за %d мс%n",
                unsafe.getProcessedCount(), expected - unsafe.getProcessedCount(), unsafeTime);
        System.out.printf("С synchronized:    %,d  (потеряно %,d)  за %d мс%n",
                safe.getProcessedCount(), expected - safe.getProcessedCount(), safeTime);
        System.out.println();
    }

    private static void duplicateTest() throws InterruptedException {
        System.out.println("=== 2. Дубли: проверка и добавление отдельно или вместе ===");
        int uniqueVacancies = 200;

        DataCollector separate = new DataCollector(0, "separate");
        runInThreads(() -> {
            for (int i = 0; i < uniqueVacancies; i++) {
                VacancyItem item = new VacancyItem("vac-" + i, "Вакансия " + i);
                if (!separate.isAlreadyProcessed(item.id())) {
                    simulateDownload();
                    separate.collectItem(item);
                }
            }
        });

        DataCollector atomic = new DataCollector(0, "atomic");
        runInThreads(() -> {
            for (int i = 0; i < uniqueVacancies; i++) {
                VacancyItem item = new VacancyItem("vac-" + i, "Вакансия " + i);
                if (atomic.collectIfNew(item)) {
                    simulateDownload();
                }
            }
        });

        System.out.printf("Уникальных вакансий:            %,d%n", uniqueVacancies);
        System.out.printf("Проверка и добавление отдельно: %,d в списке (дублей %,d)%n",
                separate.getItemCount(), separate.getItemCount() - uniqueVacancies);
        System.out.printf("collectIfNew одним вызовом:     %,d в списке (дублей %,d)%n",
                atomic.getItemCount(), atomic.getItemCount() - uniqueVacancies);
        System.out.println();
    }

    private static void waitNotifyTest() throws InterruptedException {
        System.out.println("=== 3. wait / notifyAll: отчет ждет, пока соберут данные ===");
        int perWorker = 500;
        int target = 4 * perWorker;
        DataCollector collector = new DataCollector(0, "collector");
        long start = System.currentTimeMillis();

        Thread reportBuilder = new Thread(() -> {
            try {
                collector.awaitProcessed(target);
                long elapsed = System.currentTimeMillis() - start;
                System.out.printf("[%4d мс] ReportBuilder дождался: обработано %d, в списке %d%n",
                        elapsed, collector.getProcessedCount(), collector.getItemCount());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "ReportBuilder");
        reportBuilder.start();

        List<Thread> workers = new ArrayList<>();
        for (int w = 0; w < 4; w++) {
            int workerNumber = w;
            Thread worker = new Thread(() -> {
                for (int i = 0; i < perWorker; i++) {
                    collector.collectIfNew(new VacancyItem("w" + workerNumber + "-" + i, "Вакансия"));
                    collector.incrementProcessed();
                    if (i % 100 == 0) {
                        sleepQuietly(50);
                    }
                }
            }, "Collector-" + w);
            workers.add(worker);
        }

        Thread.sleep(50);
        System.out.printf("[%4d мс] ReportBuilder ждёт %d обработанных, состояние: %s%n",
                System.currentTimeMillis() - start, target, reportBuilder.getState());
        for (Thread worker : workers) {
            worker.start();
        }
        Thread.sleep(150);
        System.out.printf("[%4d мс] Идёт сбор: обработано %d, ReportBuilder: %s%n",
                System.currentTimeMillis() - start, collector.getProcessedCount(), reportBuilder.getState());

        for (Thread worker : workers) {
            worker.join();
        }
        reportBuilder.join();
        System.out.println();
    }

    private static void deadlockTest() throws InterruptedException {
        System.out.println("=== 4. Deadlock при переносе вакансий между двумя коллекторами ===");
        int transfers = 100_000;

        DataCollector moscow = filledCollector(1, "Москва");
        DataCollector spb = filledCollector(2, "Санкт-Петербург");
        Thread t1 = new Thread(() -> {
            for (int i = 0; i < transfers; i++) {
                DataCollector.transferWithDeadlockRisk(moscow, spb);
            }
        }, "Moscow->Spb");
        Thread t2 = new Thread(() -> {
            for (int i = 0; i < transfers; i++) {
                DataCollector.transferWithDeadlockRisk(spb, moscow);
            }
        }, "Spb->Moscow");
        // Если потоки зависнут навсегда, они не помешают программе завершиться
        t1.setDaemon(true);
        t2.setDaemon(true);
        t1.start();
        t2.start();
        // join с таймаутом: ждём максимум 2 секунды, дальше считаем, что зависли
        t1.join(2000);
        t2.join(2000);
        if (t1.isAlive() || t2.isAlive()) {
            System.out.printf("Без порядка захвата: потоки зависли. %s: %s, %s: %s%n",
                    t1.getName(), t1.getState(), t2.getName(), t2.getState());
        } else {
            System.out.println("Без порядка захвата: в этот раз повезло, deadlock не случился");
        }

        // Новые коллекторы
        DataCollector moscow2 = filledCollector(1, "Москва");
        DataCollector spb2 = filledCollector(2, "Санкт-Петербург");
        long start = System.currentTimeMillis();
        Thread t3 = new Thread(() -> {
            for (int i = 0; i < transfers; i++) {
                DataCollector.transferSafe(moscow2, spb2);
            }
        }, "Moscow->Spb");
        Thread t4 = new Thread(() -> {
            for (int i = 0; i < transfers; i++) {
                DataCollector.transferSafe(spb2, moscow2);
            }
        }, "Spb->Moscow");
        t3.start();
        t4.start();
        t3.join();
        t4.join();
        long elapsed = System.currentTimeMillis() - start;
        int total = moscow2.getItemCount() + spb2.getItemCount();
        System.out.printf("С порядком захвата: завершились за %d мс, вакансий всего %d (было 2000)%n",
                elapsed, total);
    }

    private static DataCollector filledCollector(int id, String name) {
        DataCollector collector = new DataCollector(id, name);
        for (int i = 0; i < 1000; i++) {
            collector.collectItem(new VacancyItem(name + "-" + i, "Вакансия"));
        }
        return collector;
    }

    private static long runInThreads(Runnable task) throws InterruptedException {
        List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            threads.add(new Thread(task, "Worker-" + i));
        }
        long start = System.currentTimeMillis();
        for (Thread t : threads) {
            t.start();
        }
        for (Thread t : threads) {
            t.join();
        }
        return System.currentTimeMillis() - start;
    }

    // Имитация загрузки вакансии из API
    private static void simulateDownload() {
        sleepQuietly(1);
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}