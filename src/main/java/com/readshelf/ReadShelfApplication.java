



package com.readshelf;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

// @EnableScheduling activates @Scheduled methods app-wide (the outbox poller needs it).
@EnableScheduling
@SpringBootApplication
public class ReadShelfApplication {

	public static void main(String[] args) {
		SpringApplication.run(ReadShelfApplication.class, args);
	}

}
