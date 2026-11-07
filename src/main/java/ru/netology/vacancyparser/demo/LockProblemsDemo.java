package ru.netology.vacancyparser.demo;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class LockProblemsDemo {

    private static final Object resourceA = new Object();
    private static final Object resourceB = new Object();

    public static void main(String[] args) throws InterruptedException {
        deadlockPrevention();
        livelock(false);
        livelock(true);
        starvation(false);
        starvation(true);
    }

    // 1. Предотвращение deadlock
    private static void deadlockPrevention() throws InterruptedException {
        System.out.println("=== 1. Deadlock: единый порядок захвата ===");
        int rounds = 10_000;
        int[] done = new int[2];
        Thread thread1 = new Thread(() -> {
            for (int i = 0; i < rounds; i++) {
                useBothInOrder();
                done[0]++;
            }
        }, "Thread-1");
        Thread thread2 = new Thread(() -> {
            for (int i = 0; i < rounds; i++) {
                useBothInOrder();
                done[1]++;
            }
        }, "Thread-2");
        long start = System.currentTimeMillis();
        thread1.start();
        thread2.start();
        thread1.join();
        thread2.join();
        System.out.printf("Оба потока берут сначала resourceA, потом resourceB: %d + %d захватов за %d мс, зависаний нет%n%n",
                done[0], done[1], System.currentTimeMillis() - start);
    }

    private static void useBothInOrder() {
        synchronized (resourceA) {
            synchronized (resourceB) {
                Thread.onSpinWait();
            }
        }
    }

    // 2. Livelock
    private static void livelock(boolean withTimeout) throws InterruptedException {
        String title;
        if (withTimeout) {
            title = "с тайм-аутом вежливости";
        } else {
            title = "без тайм-аута";
        }
        System.out.println("=== 2. Livelock: два вежливых потока, " + title + " ===");

        AtomicReference<String> owner = new AtomicReference<>("Worker-1");
        AtomicInteger handovers = new AtomicInteger();
        boolean[] needs = {true, true};
        long[] finishedAt = new long[2];
        long start = System.currentTimeMillis();
        long deadline = start + 1000;

        List<Thread> threads = new ArrayList<>();
        for (int w = 0; w < 2; w++) {
            int me = w;
            int other = 1 - w;
            String myName = "Worker-" + (me + 1);
            String otherName = "Worker-" + (other + 1);
            threads.add(new Thread(() -> {
                long waitingSince = System.currentTimeMillis();
                while (needs[me] && System.currentTimeMillis() < deadline) {
                    if (!owner.get().equals(myName)) {
                        sleepQuietly(1);
                        continue;
                    }
                    boolean waitedTooLong = System.currentTimeMillis() - waitingSince > 20;
                    if (needs[other] && !(withTimeout && waitedTooLong)) {
                        owner.set(otherName);
                        handovers.incrementAndGet();
                        continue;
                    }
                    sleepQuietly(5);
                    needs[me] = false;
                    finishedAt[me] = System.currentTimeMillis() - start;
                    owner.set(otherName);
                }
            }, myName));
        }
        for (Thread t : threads) {
            t.start();
        }
        for (Thread t : threads) {
            t.join();
        }

        if (needs[0] || needs[1]) {
            System.out.printf("За 1 секунду ресурс передали %d раз, работу не сделал никто%n%n", handovers.get());
        } else {
            System.out.printf("Передач %d, Worker-1 закончил на %d мс, Worker-2 на %d мс%n%n",
                    handovers.get(), finishedAt[0], finishedAt[1]);
        }
    }

    // 3. Starvation
    private static void starvation(boolean aging) throws InterruptedException {
        String title;
        if (aging) {
            title = "ресурс по приоритету со старением";
        } else {
            title = "ресурс строго по приоритету";
        }
        System.out.println("=== 3. Starvation: " + title + " ===");

        PriorityResource resource = new PriorityResource(aging);
        int workers = 6;
        long start = System.currentTimeMillis();
        long deadline = start + 2000;
        int[] counts = new int[workers];
        long[] maxWait = new long[workers];
        long[] firstAt = new long[workers];
        Arrays.fill(firstAt, -1);

        List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < workers; i++) {
            int id = i;
            Thread t = new Thread(() -> {
                try {
                    while (System.currentTimeMillis() < deadline) {
                        long askedAt = System.currentTimeMillis();
                        resource.acquire();
                        try {
                            long now = System.currentTimeMillis();
                            if (now - askedAt > maxWait[id]) {
                                maxWait[id] = now - askedAt;
                            }
                            if (firstAt[id] < 0) {
                                firstAt[id] = now - start;
                            }
                            counts[id]++;
                            busyWork(1);
                        } finally {
                            resource.release();
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }, "Worker-" + (i + 1));
            if (i == workers - 1) {
                t.setPriority(Thread.MIN_PRIORITY);   // 1
            } else {
                t.setPriority(Thread.MAX_PRIORITY);   // 10
            }
            threads.add(t);
        }
        for (Thread t : threads) {
            t.start();
        }
        for (Thread t : threads) {
            t.join();
        }

        for (int i = 0; i < workers; i++) {
            System.out.printf("  Worker-%d, приоритет %2d: захватов %4d, впервые получил на %4d мс, дольше всего ждал %4d мс%n",
                    i + 1, threads.get(i).getPriority(), counts[i], firstAt[i], maxWait[i]);
        }
        System.out.println();
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