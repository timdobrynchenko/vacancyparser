package ru.netology.vacancyparser.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.netology.vacancyparser.model.Vacancy;

// Spring Data сам напишет реализацию: save, saveAll, findById, findAll, count и т. д.
// <Vacancy, String> - что храним и какого типа у него id.
public interface VacancyRepository extends JpaRepository<Vacancy, String> {
}
