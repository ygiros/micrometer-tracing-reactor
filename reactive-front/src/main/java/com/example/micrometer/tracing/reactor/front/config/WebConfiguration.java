package com.example.micrometer.tracing.reactor.front.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebConfiguration {

	private static final String DELEGATE_BASE_URL = "http://localhost:11012";

	private final WebClient.Builder webClientBuilder;

	public WebConfiguration(WebClient.Builder webClientBuilder) {
		this.webClientBuilder = webClientBuilder;
	}

	@Bean
	public WebClient webClientToDelegate() {
		return this.webClientBuilder
				.baseUrl(DELEGATE_BASE_URL)
				.build();
	}
}
