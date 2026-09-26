package ru.netology.vacancyparser.demo;

import java.util.ArrayDeque;
import java.util.Deque;

public class SynchronizedBuffer<T> implements Buffer<T> {

    private final Deque<T> items = new ArrayDeque<>();
    private final int capacity;
    private boolean closed = false;

    public SynchronizedBuffer(int capacity) {
        this.capacity = capacity;
    }

    public synchronized void put(T item) throws InterruptedException {
        while (items.size() == capacity) {
            wait();
        }
        items.addLast(item);
        notifyAll();
    }

    public synchronized T take() throws InterruptedException {
        while (items.isEmpty()) {
            if (closed) {
                return null;
            }
            wait();
        }
        T item = items.removeFirst();
        notifyAll();
        return item;
    }

    public synchronized void close() {
        closed = true;
        notifyAll();
    }
}