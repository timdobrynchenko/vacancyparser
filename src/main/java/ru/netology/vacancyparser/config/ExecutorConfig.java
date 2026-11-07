package ru.netology.vacancyparser.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Configuration
@EnableScheduling
public class ExecutorConfig {

    // Свой пул: 6 потоков с понятными именами и ограниченная очередь.
    // Если очередь заполнится, задачу выполнит тот поток, который ее отправил (CallerRunsPolicy).
    @Bean(destroyMethod = "shutdown")
    public ThreadPoolExecutor vacancyExecutor(@Value("${collector.pool-size}") int poolSize,
                                              @Value("${collector.queue-capacity}") int queueCapacity) {
        AtomicInteger counter = new AtomicInteger(1);
        ThreadFactory threadFactory = runnable -> {
            Thread thread = new Thread(runnable);
            thread.setName("vacancy-worker-" + counter.getAndIncrement());
            return thread;
        };

        return new ThreadPoolExecutor(
                poolSize,
                poolSize,
                60, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(queueCapacity),
                threadFactory,
                new ThreadPoolExecutor.CallerRunsPolicy());
    }

    // таймауты, чтобы зависший hh.ru не держал поток пула бесконечно
    @Bean
    public RestTemplate restTemplate(@Value("${hh.timeout-seconds}") int timeoutSeconds) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(timeoutSeconds));
        factory.setReadTimeout(Duration.ofSeconds(timeoutSeconds));
        return new RestTemplate(factory);
    }
}
