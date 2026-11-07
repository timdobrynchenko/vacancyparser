package ru.netology.vacancyparser.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.netology.vacancyparser.model.ParseStatus;
import ru.netology.vacancyparser.model.ParseTask;

import java.util.List;

public interface ParseTaskRepository extends JpaRepository<ParseTask, Long> {

    List<ParseTask> findTop50ByOrderByIdDesc();

    List<ParseTask> findTop50ByStatusOrderByIdDesc(ParseStatus status);

    long countByStatus(ParseStatus status);
}
