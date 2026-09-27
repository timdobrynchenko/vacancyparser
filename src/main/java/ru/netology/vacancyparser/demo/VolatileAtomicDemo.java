package ru.netology.vacancyparser.demo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.function.Supplier;

public class VolatileAtomicDemo {

    private static volatile boolean stopRequested = false;
    private static boolean stopRequestedPlain = false;
    private static final int THREADS = 8;

    public static void main(String[] args) throws InterruptedException {
        stopFlagTest();
        counterTest();
        cacheTest();
        loadTest();
    }

    // 1. Остановка потока по флагу: без volatile и с ним
    private static void stopFlagTest() throws InterruptedException {
        System.out.println("=== 1. Остановка потока через флаг ===");

        Thread plainWorker = new Thread(() -> {
            long iterations = 0;
            while (!stopRequestedPlain) {
                iterations++;
            }
            System.out.println("PlainWorker остановился после " + iterations + " итераций");
        }, "PlainWorker");
        plainWorker.setDaemon(true);
        plainWorker.start();
        Thread.sleep(500);
        stopRequestedPlain = true;
        plainWorker.join(1000);
        if (plainWorker.isAlive()) {
            System.out.println("Без volatile: флаг выставлен, но поток через секунду все еще работает ("
                    + plainWorker.getState() + ")");
        } else {
            System.out.println("Без volatile: в этот раз поток увидел флаг");
        }

        long[] result = new long[1];
        Thread volatileWorker = new Thread(() -> {
            long iterations = 0;
            while (!stopRequested) {
                iterations++;
            }
            result[0] = iterations;
        }, "VolatileWorker");
        volatileWorker.start();
        Thread.sleep(500);
        long signalTime = System.nanoTime();
        stopRequested = true;
        volatileWorker.join();
        long reaction = (System.nanoTime() - signalTime) / 1000;
        System.out.printf("С volatile: поток остановился через %d мкс после сигнала, итераций %,d%n",
                reaction, result[0]);
        System.out.println();
    }

    // 2. Счетчик
    private static void counterTest() throws InterruptedException {
        System.out.println("=== 2. Счетчик: 8 потоков по 1 000 000 инкрементов ===");
        int perThread = 1_000_000;
        int expected = THREADS * perThread;

        VolatileCounter volatileCounter = new VolatileCounter();
        long volatileTime = runInThreads(() -> {
            for (int i = 0; i < perThread; i++) {
                volatileCounter.increment();
            }
        });
        SynchronizedCounter syncCounter = new SynchronizedCounter();
        long syncTime = runInThreads(() -> {
            for (int i = 0; i < perThread; i++) {
                syncCounter.increment();
            }
        });
        AtomicCounter atomicCounter = new AtomicCounter();
        long atomicTime = runInThreads(() -> {
            for (int i = 0; i < perThread; i++) {
                atomicCounter.increment();
            }
        });

        System.out.printf("Ожидалось:              %,d%n", expected);
        System.out.printf("volatile int, ++:       %,d  за %d мс%n", volatileCounter.get(), volatileTime);
        System.out.printf("synchronized:           %,d  за %d мс%n", syncCounter.get(), syncTime);
        System.out.printf("AtomicInteger:          %,d  за %d мс%n", atomicCounter.get(), atomicTime);
        System.out.println();
    }

    // 3. Кэш: 16 потоков одновременно просят значение
    private static void cacheTest() throws InterruptedException {
        System.out.println("=== 3. Singleton-кэш: 16 потоков одновременно просят значение ===");
        NaiveCache naive = new NaiveCache();
        Set<String> naiveSeen = runCacheRace(naive::getOrCreate);
        SingletonCache cache = new SingletonCache();
        Set<String> atomicSeen = runCacheRace(cache::getOrCreate);

        System.out.printf("Без AtomicReference: создано %d, потоки получили разных объектов: %d%n",
                naive.getCreatedCount(), naiveSeen.size());
        System.out.printf("AtomicReference:     создано %d, потоки получили разных объектов: %d, в кэше: %s%n",
                cache.getCreatedCount(), atomicSeen.size(), cache.peek());
        System.out.println();
    }

    private static Set<String> runCacheRace(Supplier<String> getter) throws InterruptedException {
        Set<String> seen = Collections.synchronizedSet(Collections.newSetFromMap(new IdentityHashMap<>()));
        CountDownLatch startGate = new CountDownLatch(1);
        List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < 16; i++) {
            Thread t = new Thread(() -> {
                try {
                    startGate.await();
                    seen.add(getter.get());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            threads.add(t);
            t.start();
        }
        startGate.countDown();   // открыть ворота
        for (Thread t : threads) {
            t.join();
        }
        return seen;
    }

    // 4. Все вместе, остановка по общему volatile-флагу
    private static void loadTest() throws InterruptedException {
        System.out.println("=== 4. Нагрузка: 8 потоков 1 секунду работают со всем сразу ===");
        stopRequested = false;
        AtomicCounter operations = new AtomicCounter();
        SingletonCache cache = new SingletonCache();
        Set<String> seen = Collections.synchronizedSet(Collections.newSetFromMap(new IdentityHashMap<>()));

        List<Thread> workers = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            Thread worker = new Thread(() -> {
                String last = null;
                while (!stopRequested) {
                    last = cache.getOrCreate();
                    operations.increment();
                }
                seen.add(last);
            }, "Worker-" + i);
            workers.add(worker);
            worker.start();
        }
        Thread.sleep(1000);
        stopRequested = true;
        for (Thread worker : workers) {
            worker.join();
        }

        System.out.printf("Все потоки остановились по флагу. Операций за секунду: %,d%n", operations.get());
        System.out.printf("Значение в кэше создано %d раз, у всех потоков один и тот же объект: %s%n",
                cache.getCreatedCount(), seen.size() == 1);
    }

    private static long runInThreads(Runnable task) throws InterruptedException {
        List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            threads.add(new Thread(task));
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
}