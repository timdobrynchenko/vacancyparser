package ru.netology.vacancyparser.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

// Задача на загрузку одной вакансии. По этой таблице видно, что обработано, а что нет.
@Entity
@Table(name = "parse_tasks")
public class ParseTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String vacancyId;
    private String url;

    // кто запустил: запрос или расписание
    private String startedBy;

    @Enumerated(EnumType.STRING)
    private ParseStatus status;

    // имя потока пула, который выполнял задачу
    private String worker;

    @Column(length = 1000)
    private String error;

    private LocalDateTime createdAt;
    private LocalDateTime finishedAt;

    public ParseTask() {
    }

    public ParseTask(String vacancyId, String url, String startedBy) {
        this.vacancyId = vacancyId;
        this.url = url;
        this.startedBy = startedBy;
        this.status = ParseStatus.NEW;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public String getVacancyId() {
        return vacancyId;
    }

    public String getUrl() {
        return url;
    }

    public String getStartedBy() {
        return startedBy;
    }

    public ParseStatus getStatus() {
        return status;
    }

    public void setStatus(ParseStatus status) {
        this.status = status;
    }

    public String getWorker() {
        return worker;
    }

    public void setWorker(String worker) {
        this.worker = worker;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(LocalDateTime finishedAt) {
        this.finishedAt = finishedAt;
    }
}
