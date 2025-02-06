package com.example.micrometer.tracing.reactor.front.controller;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.observability.micrometer.Micrometer;
import reactor.core.publisher.Mono;

import java.util.Optional;

/**
 * Web controller with spans creation + spans attributes assignations
 */
@RestController
@RequestMapping("/v1/calculator")
@Slf4j
public class FrontCalculatorControllerV1 {

	private final ObservationRegistry observationRegistry;
	private final WebClient webClientToDelegate;

	public FrontCalculatorControllerV1(ObservationRegistry observationRegistry,
									   @Qualifier("webClientToDelegate") WebClient webClientToDelegate) {
		this.observationRegistry = observationRegistry;
		this.webClientToDelegate = webClientToDelegate;
	}

	@GetMapping(path = "/square")
	public Mono<ResponseEntity<Double>> getSquare(@RequestParam(value = "value") Double value) {

		return Mono.fromSupplier(() -> value)
				.doOnNext(aDouble -> log.info("Receive request to calculate square of {}", aDouble))
				.flatMap(this::computeSquare)
				.doOnNext(squareValue -> log.info("Respond result = {} to client", squareValue))
				.map(ResponseEntity::ok)
				// Name sequence (= name generated span)
				.name("getSquare-method")
				// Bind key/value pair to sequence (= set attribute to current span)
				.tag("value.from.request", value.toString())
				// Declare observation on sequence (= generate span)
				.tap(Micrometer.observation(observationRegistry));
	}

	private Mono<Double> computeSquare(Double value) {
		log.info("Request delegate to calculate square of {}", value);

		return webClientToDelegate.get()
				.uri(uriBuilder ->
						uriBuilder
								.path("/delegate/v1/calculator/square")
								.queryParam("value", value)
								.build())
				.retrieve()
				.bodyToMono(Double.class)
				// Set attributes to current span with Observation API
				.doOnNext(squareValue ->
						getCurrentObservation()
								.highCardinalityKeyValue("value.sent.to.delegate", String.valueOf(value))
								.highCardinalityKeyValue("value.received.from.delegate", String.valueOf(squareValue)))
				.name("computeSquare-method")
				.tap(Micrometer.observation(observationRegistry));
	}

	private Observation getCurrentObservation() {
		return Optional.ofNullable(observationRegistry.getCurrentObservation())
				.orElseGet(() -> {
					log.warn("No current observation found");
					return Observation.NOOP;
				});
	}
}
