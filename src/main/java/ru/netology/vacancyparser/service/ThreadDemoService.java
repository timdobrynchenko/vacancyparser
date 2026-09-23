package ru.netology.vacancyparser.service;

import org.springframework.stereotype.Service;
import ru.netology.vacancyparser.demo.CounterWorker;
import ru.netology.vacancyparser.demo.ThreadInspector;
import ru.netology.vacancyparser.task.VacancyFetchTask;

@Service
public class ThreadDemoService {

    public void runDemo() throws InterruptedException {
        ThreadInspector.printActiveThreads("до запуска");

        // Способ 1: задача Runnable + объект Thread с понятным именем
        VacancyFetchTask task = new VacancyFetchTask(1, "https://api.hh.ru/vacancies/120000001");
        Thread viaRunnable = new Thread(task, "VacancyFetcher-1");

        // Способ 2: наследник Thread, имя задано внутри него
        Thread viaInheritance = new CounterWorker(2);

        viaRunnable.start();
        viaInheritance.start();

        ThreadInspector.printActiveThreads("после start()");

        viaRunnable.join();
        viaInheritance.join();

        ThreadInspector.printActiveThreads("после join()");
    }
}