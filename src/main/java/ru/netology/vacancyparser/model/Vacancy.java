package ru.netology.vacancyparser.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

// Вакансия, как она хранится в базе H2.
// @Entity - Hibernate сам создаст таблицу VACANCIES по полям этого класса.
@Entity
@Table(name = "vacancies")
public class Vacancy {

    // id вакансии на hh.ru, например 136231014. Повторная загрузка той же вакансии обновит строку, а не создаст дубль.
    @Id
    private String id;

    private String title;
    private String company;
    private String city;

    // Зарплата от и до. Любая из границ может отсутствовать - тогда null.
    private Integer salaryFrom;
    private Integer salaryTo;
    private String currency;

    // Зарплата как ее показывает hh.ru: от 150 000 до 200 000 ₽ за месяц, на руки
    private String salaryText;

    @Column(length = 1000)
    private String skills;

    @Column(length = 4000)
    private String requirements;

    private LocalDateTime publishedAt;
    private String url;

    // Откуда взяли: hh.ru или встроенный пример
    private String source;

    private LocalDateTime collectedAt;

    // Имя потока пула, который загрузил вакансию, например vacancy-worker-3
    private String collectedBy;

    public Vacancy() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getCompany() {
        return company;
    }

    public void setCompany(String company) {
        this.company = company;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public Integer getSalaryFrom() {
        return salaryFrom;
    }

    public void setSalaryFrom(Integer salaryFrom) {
        this.salaryFrom = salaryFrom;
    }

    public Integer getSalaryTo() {
        return salaryTo;
    }

    public void setSalaryTo(Integer salaryTo) {
        this.salaryTo = salaryTo;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getSalaryText() {
        return salaryText;
    }

    public void setSalaryText(String salaryText) {
        this.salaryText = salaryText;
    }

    public String getSkills() {
        return skills;
    }

    public void setSkills(String skills) {
        this.skills = skills;
    }

    public String getRequirements() {
        return requirements;
    }

    public void setRequirements(String requirements) {
        this.requirements = requirements;
    }

    public LocalDateTime getPublishedAt() {
        return publishedAt;
    }

    public void setPublishedAt(LocalDateTime publishedAt) {
        this.publishedAt = publishedAt;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public LocalDateTime getCollectedAt() {
        return collectedAt;
    }

    public void setCollectedAt(LocalDateTime collectedAt) {
        this.collectedAt = collectedAt;
    }

    public String getCollectedBy() {
        return collectedBy;
    }

    public void setCollectedBy(String collectedBy) {
        this.collectedBy = collectedBy;
    }

    // Зарплата для сортировки: нижняя граница, а если ее нет - верхняя
    public Integer salaryForSort() {
        if (salaryFrom != null) {
            return salaryFrom;
        }
        return salaryTo;
    }
}
