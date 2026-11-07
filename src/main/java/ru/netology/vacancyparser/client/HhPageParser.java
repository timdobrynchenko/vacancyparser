package ru.netology.vacancyparser.client;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;
import ru.netology.vacancyparser.model.Vacancy;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Разбор HTML hh.ru.
// Основные поля лежат в блоке <script type="application/ld+json"> (JSON с описанием вакансии).
// Зарплаты и навыков там нет, их берем из элементов с атрибутом data-qa.
@Component
public class HhPageParser {

    private static final Pattern VACANCY_LINK = Pattern.compile("/vacancy/(\\d{5,})");
    private static final Pattern DIGITS_ONLY = Pattern.compile("^\\d{5,}$");
    private static final Pattern SALARY_FROM = Pattern.compile("от\\s*([\\d ]+)");
    private static final Pattern SALARY_TO = Pattern.compile("до\\s*([\\d ]+)");

    private final JsonMapper jsonMapper;

    public HhPageParser(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    // "https://spb.hh.ru/vacancy/136231014?from=search" или просто "136231014" -> "136231014"
    public Optional<String> extractId(String urlOrId) {
        if (urlOrId == null) {
            return Optional.empty();
        }
        String value = urlOrId.trim();
        if (DIGITS_ONLY.matcher(value).matches()) {
            return Optional.of(value);
        }
        Matcher matcher = VACANCY_LINK.matcher(value);
        if (matcher.find()) {
            return Optional.of(matcher.group(1));
        }
        return Optional.empty();
    }

    // id вакансий со страницы поиска, без повторов
    public List<String> parseSearchIds(String html) {
        Set<String> ids = new LinkedHashSet<>();
        Matcher matcher = VACANCY_LINK.matcher(html);
        while (matcher.find()) {
            ids.add(matcher.group(1));
        }
        return new ArrayList<>(ids);
    }

    public Vacancy parseVacancy(String vacancyId, String html) {
        Document page = Jsoup.parse(html);
        JsonNode posting = findJobPosting(page);
        if (posting == null) {
            throw new VacancyParseException("на странице нет данных вакансии (капча, архив или другая разметка)");
        }

        Vacancy vacancy = new Vacancy();
        vacancy.setId(vacancyId);
        vacancy.setUrl("https://hh.ru/vacancy/" + vacancyId);
        vacancy.setSource("hh.ru");
        vacancy.setTitle(plainText(posting.path("title").asString("")));
        vacancy.setCompany(plainText(posting.path("hiringOrganization").path("name").asString("")));
        vacancy.setCity(posting.path("jobLocation").path("address").path("addressLocality").asString(""));
        vacancy.setPublishedAt(parseDate(posting.path("datePosted").asString("")));
        vacancy.setRequirements(extractRequirements(plainText(posting.path("description").asString(""))));

        fillSalary(vacancy, page);
        vacancy.setSkills(extractSkills(page));
        return vacancy;
    }

    private JsonNode findJobPosting(Document page) {
        for (Element script : page.select("script[type=application/ld+json]")) {
            try {
                JsonNode node = jsonMapper.readTree(script.data());
                if ("JobPosting".equals(node.path("@type").asString(""))) {
                    return node;
                }
            } catch (Exception e) {
                // блоков несколько, если какой-то не читается - смотрим следующий
            }
        }
        return null;
    }

    // до 180 000 ₽ за месяц на руки: числа после от и до и валюта
    private void fillSalary(Vacancy vacancy, Document page) {
        Element salary = page.selectFirst("[data-qa=vacancy-salary]");
        if (salary == null) {
            return;
        }
        String text = normalizeSpaces(salary.text());
        vacancy.setSalaryText(text);
        vacancy.setSalaryFrom(findNumber(SALARY_FROM, text));
        vacancy.setSalaryTo(findNumber(SALARY_TO, text));
        if (text.contains("₽")) {
            vacancy.setCurrency("RUB");
        } else if (text.contains("$")) {
            vacancy.setCurrency("USD");
        } else if (text.contains("€")) {
            vacancy.setCurrency("EUR");
        }
    }

    private Integer findNumber(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        if (!matcher.find()) {
            return null;
        }
        String digits = matcher.group(1).replace(" ", "");
        if (digits.isEmpty()) {
            return null;
        }
        return Integer.parseInt(digits);
    }

    private String extractSkills(Document page) {
        Set<String> skills = new LinkedHashSet<>();
        for (Element skill : page.select("[data-qa=skills-element]")) {
            String name = skill.text().trim();
            if (!name.isEmpty()) {
                skills.add(name);
            }
        }
        return String.join(", ", skills);
    }

    // Текст с места, где начинается Требования, не длиннее 1000 символов
    private String extractRequirements(String description) {
        String text = description;
        int start = text.indexOf("Требования");
        if (start >= 0) {
            text = text.substring(start);
        }
        if (text.length() > 1000) {
            text = text.substring(0, 1000) + "…";
        }
        return text;
    }

    private LocalDateTime parseDate(String value) {
        if (value.isEmpty()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(value).toLocalDateTime();
        } catch (Exception e) {
            return null;
        }
    }

    private String plainText(String html) {
        return normalizeSpaces(Jsoup.parse(html).text());
    }

    // hh.ru пишет 180 000 с неразрывным пробелом
    private String normalizeSpaces(String text) {
        return text.replace(' ', ' ').replace(' ', ' ').replace(' ', ' ').trim();
    }
}
