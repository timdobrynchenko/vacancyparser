package ru.netology.vacancyparser.demo;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class BoundedBuffer<T> implements Buffer<T> {

    private final Deque<T> items = new ArrayDeque<>();
    private final int capacity;
    private final boolean log;
    private final long startTime = System.currentTimeMillis();

    private final ReentrantLock lock = new ReentrantLock();
    private final Condition notFull = lock.newCondition();
    private final Condition notEmpty = lock.newCondition();
    private boolean closed = false;

    public BoundedBuffer(int capacity, boolean log) {
        this.capacity = capacity;
        this.log = log;
    }

    public void put(T item) throws InterruptedException {
        lock.lock();
        try {
            while (items.size() == capacity) {
                log("ждет: буфер полон");
                notFull.await();
            }
            items.addLast(item);
            log("положил " + item + "  (в буфере " + items.size() + "/" + capacity + ")");
            notEmpty.signal();
        } finally {
            lock.unlock();
        }
    }

    public T take() throws InterruptedException {
        lock.lock();
        try {
            while (items.isEmpty()) {
                if (closed) {
                    return null;
                }
                log("ждет: буфер пуст");
                notEmpty.await();
            }
            T item = items.removeFirst();
            log("взял " + item + "  (в буфере " + items.size() + "/" + capacity + ")");
            notFull.signal();
            return item;
        } finally {
            lock.unlock();
        }
    }

    public void close() {
        lock.lock();
        try {
            closed = true;
            log("буфер закрыт, будим всех ожидающих");
            notEmpty.signalAll();
        } finally {
            lock.unlock();
        }
    }

    private void log(String message) {
        if (log) {
            System.out.printf("[%4d мс] %-11s %s%n",
                    System.currentTimeMillis() - startTime, Thread.currentThread().getName(), message);
        }
    }
}