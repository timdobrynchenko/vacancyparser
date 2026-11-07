package ru.netology.vacancyparser.client;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import ru.netology.vacancyparser.model.Vacancy;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

// 12 придуманных вакансий из resources/sample/vacancies.json на случай, если hh.ru недоступен
@Component
public class SampleVacancies {

    private final Map<String, Vacancy> byId = new ConcurrentHashMap<>();
    private final List<String> ids;

    public SampleVacancies(JsonMapper jsonMapper) throws IOException {
        List<String> order = new ArrayList<>();
        try (InputStream in = new ClassPathResource("sample/vacancies.json").getInputStream()) {
            List<Vacancy> list = jsonMapper.readValue(in, new TypeReference<List<Vacancy>>() {
            });
            for (Vacancy vacancy : list) {
                byId.put(vacancy.getId(), vacancy);
                order.add(vacancy.getId());
            }
        }
        this.ids = Collections.unmodifiableList(order);
    }

    // копия, чтобы разные потоки не меняли один и тот же объект
    public Optional<Vacancy> findById(String id) {
        Vacancy original = byId.get(id);
        if (original == null) {
            return Optional.empty();
        }
        return Optional.of(copy(original));
    }

    public List<String> searchIds(String text, int limit) {
        String query = text.toLowerCase();
        List<String> result = new ArrayList<>();
        for (String id : ids) {
            Vacancy vacancy = byId.get(id);
            String haystack = (vacancy.getTitle() + " " + vacancy.getSkills()).toLowerCase();
            if (haystack.contains(query)) {
                result.add(id);
            }
            if (result.size() >= limit) {
                break;
            }
        }
        return result;
    }

    private Vacancy copy(Vacancy source) {
        Vacancy vacancy = new Vacancy();
        vacancy.setId(source.getId());
        vacancy.setTitle(source.getTitle());
        vacancy.setCompany(source.getCompany());
        vacancy.setCity(source.getCity());
        vacancy.setSalaryFrom(source.getSalaryFrom());
        vacancy.setSalaryTo(source.getSalaryTo());
        vacancy.setCurrency(source.getCurrency());
        vacancy.setSalaryText(source.getSalaryText());
        vacancy.setSkills(source.getSkills());
        vacancy.setRequirements(source.getRequirements());
        vacancy.setPublishedAt(source.getPublishedAt());
        vacancy.setUrl(source.getUrl());
        vacancy.setSource(source.getSource());
        return vacancy;
    }
}
