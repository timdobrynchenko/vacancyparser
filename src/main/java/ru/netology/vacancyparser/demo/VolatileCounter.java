package ru.netology.vacancyparser.demo;

public class VolatileCounter {

    private volatile int value = 0;

    public void increment() {
        value++;
    }

    public int get() {
        return value;
    }
}