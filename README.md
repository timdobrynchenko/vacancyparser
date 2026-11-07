# vacancyparser

Итоговый проект по курсу "Теория и практика многопоточности", вариант 3: многопоточный парсер вакансий hh.ru.

Spring Boot приложение загружает страницы вакансий в пуле потоков, достает название, компанию, зарплату, требования, город и дату публикации и сохраняет их в H2. Результаты отдаются через `/answer` с фильтрами, сортировкой и пагинацией.

## Стек

- Java 21, Spring Boot 4, Maven
- Spring Web MVC, Spring Data JPA, H2
- RestTemplate для запросов к hh.ru, jsoup для разбора HTML

## Запуск

```bash
./mvnw spring-boot:run
```

Или открыть проект в IntelliJ IDEA и запустить `VacancyparserApplication`. Порт `8080`, настраивать ничего не нужно.

Через 5 секунд после старта сам запускается сбор по расписанию: 15 вакансий по запросу "java" в Санкт-Петербурге. В логах видно, как потоки `vacancy-worker-N` ходят на hh.ru и как меняются статусы задач:

```
[vacancy-worker-1] GET https://hh.ru/vacancy/138962554
[vacancy-worker-1] задача #1 (вакансия 138962554): IN_PROGRESS
...
задача #1 (вакансия 138962554): DONE
Сбор (расписание) завершен: запрошено 15, сохранено 15, ошибок 0, потоки {vacancy-worker-1=3, ...}
```

После этого `/answer` уже возвращает данные.

## Эндпоинты

Все запросы есть в файле [`requests.http`](requests.http), в IntelliJ IDEA их можно запускать кнопкой рядом с запросом.

| Метод | URL | Что делает |
|---|---|---|
| POST | `http://localhost:8080/api/vacancies/collect` | собрать вакансии по ссылкам |
| POST | `http://localhost:8080/api/vacancies/search` | собрать первые вакансии из поиска hh.ru |
| GET | `http://localhost:8080/answer` | результаты из H2: фильтр, сортировка, страницы |
| GET | `http://localhost:8080/answer/{id}` | одна вакансия |
| GET | `http://localhost:8080/api/tasks` | последние 50 задач и их статусы |
| GET | `http://localhost:8080/api/status` | состояние пула и последний запуск |

### POST /api/vacancies/collect

Тело запроса: список ссылок на вакансии hh.ru (до 50).

```json
{
  "urls": [
    "https://hh.ru/vacancy/128072467",
    "https://spb.hh.ru/vacancy/136231014?from=search"
  ]
}
```

```bash
curl -X POST http://localhost:8080/api/vacancies/collect \
  -H "Content-Type: application/json" \
  -d '{"urls":["https://hh.ru/vacancy/128072467","https://hh.ru/vacancy/136231014"]}'
```

Ответ:

```json
{
  "trigger": "запрос",
  "source": "hh.ru",
  "requested": 2,
  "saved": 2,
  "skipped": 0,
  "errors": [],
  "loadedByThread": { "vacancy-worker-1": 1, "vacancy-worker-2": 1 },
  "durationMs": 1077,
  "finishedAt": "2026-11-04T13:01:40.419"
}
```

Вакансии на hh.ru снимают с публикации. Если ссылка устарела, в `errors` будет `hh.ru ответил 404`.

### POST /api/vacancies/search

Тело запроса: текст поиска, регион hh.ru (`1` Москва, `2` Санкт-Петербург, `113` Россия) и сколько вакансий взять (до 50).

```json
{ "text": "java", "area": 2, "limit": 10 }
```

```bash
curl -X POST http://localhost:8080/api/vacancies/search \
  -H "Content-Type: application/json" \
  -d '{"text":"java","area":2,"limit":10}'
```

Ответ такой же, как у `/collect`.

### GET /answer

```
http://localhost:8080/answer
http://localhost:8080/answer?sort=salary&order=desc&page=0&size=5
http://localhost:8080/answer?city=Санкт-Петербург&minSalary=150000
http://localhost:8080/answer?text=spring
```

| Параметр | Описание | По умолчанию |
|---|---|---|
| `city` | город, часть названия | |
| `company` | компания, часть названия | |
| `text` | слово в названии или навыках | |
| `minSalary` | зарплата не ниже (сравнивается верхняя граница) | |
| `sort` | `date`, `salary`, `title`, `company` | `date` |
| `order` | `asc`, `desc` | `desc` |
| `page` | номер страницы с 0 | `0` |
| `size` | размер страницы, до 100 | `10` |

Ответ: `total` (сколько подошло), `page`, `size`, `items` (вакансии).

### GET /api/tasks

```
http://localhost:8080/api/tasks
http://localhost:8080/api/tasks?status=ERROR
```

## Статусы задач

Каждая вакансия загружается отдельной задачей. Задача хранится в таблице `PARSE_TASKS` и проходит статусы:

```
NEW -> IN_PROGRESS -> DONE
                   -> ERROR
                   -> CANCELLED
```

- `NEW`: задача создана и отдана в пул
- `IN_PROGRESS`: поток пула начал загрузку (статус ставит сам поток)
- `DONE`: вакансия сохранена в `VACANCIES`
- `ERROR`: страница не загрузилась или на ней нет данных вакансии (текст ошибки в поле `error`)
- `CANCELLED`: задача не уложилась в таймаут 60 секунд, `invokeAll` ее отменил

Каждая смена статуса пишется в лог. Сколько задач в каждом статусе, видно в `/api/status`.

## Структура базы данных

База H2 в памяти: `http://localhost:8080/h2-console`, JDBC URL `jdbc:h2:mem:vacancies`, пользователь `sa`, пароль пустой.

Таблица `VACANCIES`:

| Колонка | Тип | Описание |
|---|---|---|
| `id` | varchar, PK | id вакансии на hh.ru |
| `title` | varchar | название |
| `company` | varchar | компания |
| `city` | varchar | город |
| `salary_from`, `salary_to` | integer | зарплата от и до |
| `currency` | varchar | `RUB`, `USD`, `EUR` |
| `salary_text` | varchar | зарплата как на сайте |
| `skills` | varchar(1000) | ключевые навыки |
| `requirements` | varchar(4000) | требования из описания |
| `published_at` | timestamp | дата публикации |
| `url` | varchar | ссылка |
| `source` | varchar | `hh.ru` или `пример` |
| `collected_at` | timestamp | когда загружена |
| `collected_by` | varchar | какой поток загрузил |

Таблица `PARSE_TASKS`:

| Колонка | Тип | Описание |
|---|---|---|
| `id` | bigint, PK | номер задачи |
| `vacancy_id` | varchar | id вакансии |
| `url` | varchar | ссылка |
| `started_by` | varchar | `запрос` или `расписание` |
| `status` | varchar | статус задачи |
| `worker` | varchar | поток, который выполнял задачу |
| `error` | varchar(1000) | текст ошибки |
| `created_at`, `finished_at` | timestamp | создание и завершение |

## Структура проекта

```
src/main/java/ru/netology/vacancyparser
├── VacancyparserApplication.java
├── config
│   └── ExecutorConfig.java           пул потоков, RestTemplate, @EnableScheduling
├── controller
│   └── VacancyController.java        все эндпоинты
├── service
│   ├── VacancyCollectorService.java  раздает задачи пулу, сохраняет результаты и статусы
│   ├── ScheduledCollector.java       сбор по расписанию
│   └── VacancyQueryService.java      фильтр и сортировка для /answer
├── task
│   └── VacancyLoadTask.java          Callable: загрузка одной вакансии
├── client
│   ├── HhClient.java                 HTTP-запросы к hh.ru
│   ├── HhPageParser.java             разбор HTML страницы
│   ├── SampleVacancies.java          запасные данные
│   └── VacancyParseException.java
├── model                             Vacancy, ParseTask, ParseStatus и объекты запросов/ответов
├── repository                        VacancyRepository, ParseTaskRepository
└── demo                              классы из домашних заданий, приложение их не запускает
src/main/resources
├── application.properties            настройки пула, расписания, hh.ru
└── sample/vacancies.json             12 вакансий-примеров
```

## Многопоточность

| Что | Где |
|---|---|
| Свой ExecutorService | `ExecutorConfig`: `ThreadPoolExecutor` на 6 потоков `vacancy-worker-N`, очередь `ArrayBlockingQueue` на 100 задач, `CallerRunsPolicy` |
| Задачи с результатом | `VacancyLoadTask implements Callable<Vacancy>` |
| Таймаут | `invokeAll(tasks, 60, SECONDS)` в `VacancyCollectorService`: не успевшие задачи отменяются |
| Расписание | `ScheduledCollector`: `@Scheduled(fixedDelay)`, первый запуск через 5 секунд, дальше раз в 30 минут |
| Запросы к внешнему сервису | `HhClient`: `RestTemplate` с таймаутами |
| Ограничение нагрузки на hh.ru | `Semaphore` на 3 одновременных запроса и пауза 300 мс |
| Потокобезопасные структуры | `ConcurrentHashMap.newKeySet()` (вакансии в работе), `ConcurrentHashMap.merge` (сколько загрузил каждый поток), `AtomicLong` (число запусков), `AtomicReference` (последний отчет) |
| parallelStream | `VacancyQueryService`: фильтр, сортировка и пагинация в `/answer` |

## Почему страницы, а не API hh.ru

API hh.ru с весны 2026 года без авторизации отвечает 403:

```bash
curl -s -o /dev/null -w "%{http_code}\n" "https://api.hh.ru/vacancies?text=java"
# 403
curl -s -o /dev/null -w "%{http_code}\n" -A "Mozilla/5.0" "https://hh.ru/search/vacancy?text=java"
# 200
```

Поэтому приложение загружает обычные страницы вакансий. Название, компания, город, дата и описание берутся из блока `<script type="application/ld+json">`, зарплата и навыки из элементов с атрибутом `data-qa`.

Если hh.ru недоступен или показал капчу и ни одна вакансия не загрузилась, сбор по поиску берет 12 вакансий-примеров из `sample/vacancies.json`. В ответе тогда `"source": "пример (hh.ru недоступен)"`.

## Настройки

`src/main/resources/application.properties`:

| Свойство | Значение |
|---|---|
| `collector.pool-size` | 6 потоков |
| `collector.queue-capacity` | 100 задач |
| `collector.task-timeout-seconds` | 60 |
| `collector.schedule.enabled` | `true`, сбор по расписанию |
| `collector.schedule.delay-ms` | 1800000 (30 минут) |
| `hh.max-parallel-requests` | 3 |
| `hh.fallback-to-sample` | `true` |
