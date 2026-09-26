package ru.netology.vacancyparser.demo;

public interface Buffer<T> {

    void put(T item) throws InterruptedException;

    T take() throws InterruptedException;

    void close();
}