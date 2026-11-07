package ru.netology.vacancyparser.demo;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;

public class DeadlockDemo {

    private static final Object resourceA = new Object();
    private static final Object resourceB = new Object();

    public static void main(String[] args) throws InterruptedException {
        long pid = ProcessHandle.current().pid();
        System.out.println("PID процесса: " + pid);

        // Thread-1: сначала resourceA, потом resourceB
        Thread thread1 = new Thread(() -> {
            synchronized (resourceA) {
                System.out.println("Thread-1 захватил resourceA, теперь хочет resourceB");
                sleepQuietly(100);   // пауза, чтобы Thread-2 успел взять свой ресурс
                synchronized (resourceB) {
                    System.out.println("Thread-1 захватил оба ресурса");
                }
            }
        }, "Thread-1");

        // Thread-2: в обратном порядке, сначала resourceB, потом resourceA
        Thread thread2 = new Thread(() -> {
            synchronized (resourceB) {
                System.out.println("Thread-2 захватил resourceB, теперь хочет resourceA");
                sleepQuietly(100);
                synchronized (resourceA) {
                    System.out.println("Thread-2 захватил оба ресурса");
                }
            }
        }, "Thread-2");

        thread1.start();
        thread2.start();
        Thread.sleep(1000);

        ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
        long[] deadlocked = threadBean.findDeadlockedThreads();
        if (deadlocked == null) {
            System.out.println("Deadlock не обнаружен");
        } else {
            System.out.println("JVM нашла deadlock:");
            for (ThreadInfo info : threadBean.getThreadInfo(deadlocked)) {
                System.out.printf("  %s: %s, ждет %s, который держит %s%n",
                        info.getThreadName(), info.getThreadState(),
                        info.getLockName(), info.getLockOwnerName());
            }
        }
        System.out.println("Программа зависла. Для дампа в терминале: jstack " + pid);
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}