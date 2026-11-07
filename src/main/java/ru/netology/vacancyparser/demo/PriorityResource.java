package ru.netology.vacancyparser.demo;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class PriorityResource {

    private static final double MS_PER_AGING_POINT = 2.0;

    private final ReentrantLock lock = new ReentrantLock();
    private final Condition changed = lock.newCondition();
    private final List<Request> waiting = new ArrayList<>();
    private final boolean aging;
    private boolean busy = false;

    public PriorityResource(boolean aging) {
        this.aging = aging;
    }

    private static class Request {
        final int priority;
        final long since;

        Request(int priority, long since) {
            this.priority = priority;
            this.since = since;
        }
    }

    public void acquire() throws InterruptedException {
        lock.lock();
        try {
            Request me = new Request(Thread.currentThread().getPriority(), System.nanoTime());
            waiting.add(me);
            while (busy || chooseNext() != me) {
                changed.await();
            }
            waiting.remove(me);
            busy = true;
        } finally {
            lock.unlock();
        }
    }

    public void release() {
        lock.lock();
        try {
            busy = false;
            changed.signalAll();
        } finally {
            lock.unlock();
        }
    }

    private Request chooseNext() {
        long now = System.nanoTime();
        Request best = null;
        double bestScore = 0;
        for (Request r : waiting) {
            double score = r.priority;
            if (aging) {
                score = score + (now - r.since) / 1_000_000.0 / MS_PER_AGING_POINT;
            }
            if (best == null || score > bestScore) {
                best = r;
                bestScore = score;
            }
        }
        return best;
    }
}