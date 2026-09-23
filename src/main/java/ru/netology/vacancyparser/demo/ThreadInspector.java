package ru.netology.vacancyparser.demo;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class ThreadInspector {

    public static void printActiveThreads(String label) {
        System.out.println("--- активные потоки: " + label + " ---");

        List<Thread> threads = new ArrayList<>(Thread.getAllStackTraces().keySet());
        threads.sort(Comparator.comparing(Thread::getName));

        for (Thread t : threads) {
            System.out.printf("%-28s %-14s daemon=%b%n",
                    t.getName(), t.getState(), t.isDaemon());
        }
    }
}