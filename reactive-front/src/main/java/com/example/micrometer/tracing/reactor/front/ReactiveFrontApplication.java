package com.example.micrometer.tracing.reactor.front;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ReactiveFrontApplication {
	public static void main(String[] args) {
		SpringApplication.run(ReactiveFrontApplication.class, args);
	}
}