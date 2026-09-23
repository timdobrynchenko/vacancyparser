package ru.netology.vacancyparser;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import ru.netology.vacancyparser.service.ThreadDemoService;

// @SpringBootApplication — «здесь начинается Spring-приложение».
// Spring просканирует этот пакет и все вложенные и найдёт ваш @Service.
@SpringBootApplication
public class VacancyparserApplication {

	public static void main(String[] args) {
		SpringApplication.run(VacancyparserApplication.class, args);
	}

	// @Bean CommandLineRunner — код, который Spring выполнит сразу после старта.
	// Параметр service Spring передаст сам: он уже создал ThreadDemoService.
	@Bean
	CommandLineRunner demo(ThreadDemoService service) {
		return args -> service.runDemo();
	}
}