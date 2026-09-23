package ru.netology.vacancyparser.demo;

public class CounterWorker extends Thread {

    private final int number;

    public CounterWorker(int number) {
        super("CounterWorker-" + number);
        this.number = number;
    }

    @Override
    public void run() {
        System.out.println("[" + getName() + "] задача №" + number + " считает вакансии");
    }
}