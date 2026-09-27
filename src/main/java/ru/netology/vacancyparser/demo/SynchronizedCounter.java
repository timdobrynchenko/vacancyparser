package ru.netology.vacancyparser.demo;

public class SynchronizedCounter {

    private int value = 0;

    public synchronized void increment() {
        value++;
    }

    public synchronized int get() {
        return value;
    }
}