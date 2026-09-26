package ru.netology.vacancyparser.demo;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

public class BufferDemo {

    public static void main(String[] args) throws InterruptedException {
        interactionDemo();
        performanceTest();
    }

    // 1. Медленный сценарий с логом
    private static void interactionDemo() throws InterruptedException {
        System.out.println("=== 1. Взаимодействие: 2 producer, 2 consumer, буфер на 3 ===");
        BoundedBuffer<String> buffer = new BoundedBuffer<>(3, true);

        List<Thread> consumers = new ArrayList<>();
        for (int c = 1; c <= 2; c++) {
            Thread consumer = new Thread(() -> {
                try {
                    while (true) {
                        String item = buffer.take();
                        if (item == null) {
                            break;
                        }
                        Thread.sleep(120);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }, "Consumer-" + c);
            consumers.add(consumer);
            consumer.start();
        }

        Thread.sleep(50);
        List<Thread> producers = new ArrayList<>();
        for (int p = 1; p <= 2; p++) {
            int producerNumber = p;
            Thread producer = new Thread(() -> {
                try {
                    for (int i = 1; i <= 4; i++) {
                        buffer.put("vac-" + producerNumber + "-" + i);
                        Thread.sleep(30);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }, "Producer-" + p);
            producers.add(producer);
            producer.start();
        }

        for (Thread t : producers) {
            t.join();
        }
        buffer.close();
        for (Thread t : consumers) {
            t.join();
        }
        System.out.println();
    }

    // 2. Быстрый сценарий без лога
    private static void performanceTest() throws InterruptedException {
        System.out.println("=== 2. Корректность и скорость: 4 producer, 4 consumer, буфер на 100 ===");
        int producers = 4;
        int consumers = 4;
        int perProducer = 250_000;
        int capacity = 100;
        int runs = 5;
        long expectedCount = (long) producers * perProducer;

        long[] lockTimes = new long[runs];
        long[] syncTimes = new long[runs];
        for (int r = 0; r < runs; r++) {
            lockTimes[r] = runOnce(new BoundedBuffer<>(capacity, false), producers, consumers, perProducer, expectedCount);
            syncTimes[r] = runOnce(new SynchronizedBuffer<>(capacity), producers, consumers, perProducer, expectedCount);
        }
        Arrays.sort(lockTimes);
        Arrays.sort(syncTimes);
        System.out.printf("Передано элементов за прогон: %,d%n", expectedCount);
        System.out.printf("ReentrantLock + Condition:  медиана %d мс%n", lockTimes[runs / 2]);
        System.out.printf("synchronized + wait/notify: медиана %d мс%n", syncTimes[runs / 2]);
    }

    // Один прогон: запускает всех, ждет, проверяет, что дошло все и по одному разу
    private static long runOnce(Buffer<Integer> buffer, int producers, int consumers,
                                int perProducer, long expectedCount) throws InterruptedException {
        AtomicLong consumedCount = new AtomicLong();
        AtomicLong consumedSum = new AtomicLong();
        List<Thread> producerThreads = new ArrayList<>();
        List<Thread> consumerThreads = new ArrayList<>();

        for (int c = 0; c < consumers; c++) {
            consumerThreads.add(new Thread(() -> {
                try {
                    while (true) {
                        Integer item = buffer.take();
                        if (item == null) {
                            break;
                        }
                        consumedCount.incrementAndGet();
                        consumedSum.addAndGet(item);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }));
        }
        for (int p = 0; p < producers; p++) {
            producerThreads.add(new Thread(() -> {
                try {
                    for (int i = 1; i <= perProducer; i++) {
                        buffer.put(i);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }));
        }

        long start = System.currentTimeMillis();
        for (Thread t : consumerThreads) {
            t.start();
        }
        for (Thread t : producerThreads) {
            t.start();
        }
        for (Thread t : producerThreads) {
            t.join();
        }
        buffer.close();
        for (Thread t : consumerThreads) {
            t.join();
        }
        long elapsed = System.currentTimeMillis() - start;

        long expectedSum = (long) producers * perProducer * (perProducer + 1L) / 2;
        if (consumedCount.get() != expectedCount || consumedSum.get() != expectedSum) {
            System.out.println("ОШИБКА: потеряны или задвоены элементы");
        }
        return elapsed;
    }
}