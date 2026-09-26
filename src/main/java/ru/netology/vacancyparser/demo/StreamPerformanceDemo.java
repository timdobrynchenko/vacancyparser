package ru.netology.vacancyparser.demo;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class StreamPerformanceDemo {

    private static final int SIZE = 1_000_000;
    private static final int WARMUP_RUNS = 5;
    private static final int MEASURED_RUNS = 10;

    private static volatile Object lastResult;

    public static void main(String[] args) {
        System.out.println("Ядер процессора: " + Runtime.getRuntime().availableProcessors());
        System.out.println("Потоков в пуле parallelStream: " + ForkJoinPool.commonPool().getParallelism());

        List<Integer> numbers = createRandomList(SIZE);
        System.out.println("Создан список из " + numbers.size() + " случайных чисел");
        System.out.println();
        System.out.printf("%-34s %12s %12s%n", "Операция", "первый, мс", "медиана, мс");

        // Фильтрация: только четные
        measure("фильтрация, stream", () -> numbers.stream()
                .filter(n -> n % 2 == 0)
                .collect(Collectors.toList()));
        measure("фильтрация, parallelStream", () -> numbers.parallelStream()
                .filter(n -> n % 2 == 0)
                .collect(Collectors.toList()));

        // Преобразование: каждое умножить на 2
        measure("преобразование, stream", () -> numbers.stream()
                .map(n -> n * 2)
                .collect(Collectors.toList()));
        measure("преобразование, parallelStream", () -> numbers.parallelStream()
                .map(n -> n * 2)
                .collect(Collectors.toList()));

        // Агрегация: сумма
        measure("агрегация, stream", () -> numbers.stream()
                .mapToLong(n -> n.longValue())
                .sum());
        measure("агрегация, parallelStream", () -> numbers.parallelStream()
                .mapToLong(n -> n.longValue())
                .sum());

        // Все
        measure("все вместе, stream", () -> numbers.stream()
                .filter(n -> n % 2 == 0)
                .mapToLong(n -> n * 2L)
                .sum());
        measure("все вместе, parallelStream", () -> numbers.parallelStream()
                .filter(n -> n % 2 == 0)
                .mapToLong(n -> n * 2L)
                .sum());

        // СЛожные вычисления
        measure("тяжелая операция, stream", () -> numbers.stream()
                .mapToDouble(n -> Math.sqrt(n) * Math.sin(n))
                .sum());
        measure("тяжелая операция, parallelStream", () -> numbers.parallelStream()
                .mapToDouble(n -> Math.sqrt(n) * Math.sin(n))
                .sum());

        // Проверка
        long sequentialSum = numbers.stream().filter(n -> n % 2 == 0).mapToLong(n -> n * 2L).sum();
        long parallelSum = numbers.parallelStream().filter(n -> n % 2 == 0).mapToLong(n -> n * 2L).sum();
        System.out.println();
        System.out.println("Результат stream:         " + sequentialSum);
        System.out.println("Результат parallelStream: " + parallelSum);
        if (sequentialSum == parallelSum) {
            System.out.println("Результаты совпадают");
        } else {
            System.out.println("Результаты разные");
        }
    }

    private static List<Integer> createRandomList(int size) {
        Random random = new Random(42);
        List<Integer> list = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            list.add(random.nextInt(1000));
        }
        return list;
    }

    private static void measure(String name, Supplier<Object> operation) {
        long first = timeOnce(operation);
        for (int i = 1; i < WARMUP_RUNS; i++) {
            timeOnce(operation);
        }
        long[] times = new long[MEASURED_RUNS];
        for (int i = 0; i < MEASURED_RUNS; i++) {
            times[i] = timeOnce(operation);
        }
        Arrays.sort(times);
        long median = times[MEASURED_RUNS / 2];
        System.out.printf("%-34s %12.2f %12.2f%n", name, first / 1_000_000.0, median / 1_000_000.0);
    }
    
    private static long timeOnce(Supplier<Object> operation) {
        long start = System.nanoTime();
        Object result = operation.get();
        long elapsed = System.nanoTime() - start;
        lastResult = result;
        return elapsed;
    }
}