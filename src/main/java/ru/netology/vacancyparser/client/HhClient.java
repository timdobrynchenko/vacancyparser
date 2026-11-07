package ru.netology.vacancyparser.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Semaphore;

// Загружает HTML-страницы hh.ru через RestTemplate
@Component
public class HhClient {

    private static final Logger log = LoggerFactory.getLogger(HhClient.class);

    // без браузерного User-Agent hh.ru может показать капчу
    private static final String USER_AGENT =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 14_0) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130 Safari/537.36";

    private final RestTemplate restTemplate;
    private final String baseUrl;
    private final long pauseMs;

    // не больше 3 запросов к hh.ru одновременно, даже если потоков в пуле больше
    private final Semaphore permits;

    public HhClient(RestTemplate restTemplate,
                    @Value("${hh.base-url}") String baseUrl,
                    @Value("${hh.max-parallel-requests}") int maxParallelRequests,
                    @Value("${hh.request-pause-ms}") long pauseMs) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl;
        this.pauseMs = pauseMs;
        this.permits = new Semaphore(maxParallelRequests);
    }

    // https://hh.ru/vacancy/136231014
    public String loadVacancyPage(String vacancyId) throws InterruptedException {
        URI uri = UriComponentsBuilder.fromUriString(baseUrl)
                .path("/vacancy/{id}")
                .buildAndExpand(vacancyId)
                .toUri();
        return get(uri);
    }

    // https://hh.ru/search/vacancy?text=java&area=2
    public String loadSearchPage(String text, int area) throws InterruptedException {
        URI uri = UriComponentsBuilder.fromUriString(baseUrl)
                .path("/search/vacancy")
                .queryParam("text", text)
                .queryParam("area", area)
                .encode()
                .build()
                .toUri();
        return get(uri);
    }

    public int availablePermits() {
        return permits.availablePermits();
    }

    private String get(URI uri) throws InterruptedException {
        permits.acquire();
        try {
            // пауза, чтобы не слать запросы подряд
            Thread.sleep(pauseMs);
            log.info("[{}] GET {}", Thread.currentThread().getName(), uri);

            HttpHeaders headers = new HttpHeaders();
            headers.set(HttpHeaders.USER_AGENT, USER_AGENT);
            headers.set(HttpHeaders.ACCEPT, "text/html");
            headers.set(HttpHeaders.ACCEPT_LANGUAGE, "ru-RU,ru");

            ResponseEntity<byte[]> response;
            try {
                response = restTemplate.exchange(uri, HttpMethod.GET, new HttpEntity<>(headers), byte[].class);
            } catch (HttpStatusCodeException e) {
                // в тексте исключения лежит вся HTML-страница ошибки, оставляем только код
                throw new IllegalStateException("hh.ru ответил " + e.getStatusCode().value());
            }
            byte[] body = response.getBody();
            if (body == null) {
                return "";
            }
            return new String(body, StandardCharsets.UTF_8);
        } finally {
            permits.release();
        }
    }
}
