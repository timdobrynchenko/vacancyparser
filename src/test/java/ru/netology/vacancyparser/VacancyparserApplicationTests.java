package ru.netology.vacancyparser;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

// Сбор по расписанию в тесте выключен: тест не должен ходить на hh.ru
@SpringBootTest(properties = "collector.schedule.enabled=false")
class VacancyparserApplicationTests {

	@Test
	void contextLoads() {
	}

}
