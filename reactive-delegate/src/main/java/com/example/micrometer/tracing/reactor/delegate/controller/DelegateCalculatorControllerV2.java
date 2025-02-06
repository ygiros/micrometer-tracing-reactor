package com.example.micrometer.tracing.reactor.delegate.controller;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.tracing.otel.bridge.OtelTracer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.observability.micrometer.Micrometer;
import reactor.core.publisher.Mono;

import java.util.Objects;
import java.util.Optional;

@RestController
@RequestMapping("delegate/v2/calculator")
@Slf4j
public class DelegateCalculatorControllerV2 {

	private final ObservationRegistry observationRegistry;
	private final OtelTracer otelTracer;

	public DelegateCalculatorControllerV2(ObservationRegistry observationRegistry,
										  @Qualifier("micrometerOtelTracer") OtelTracer otelTracer) {
		this.observationRegistry = observationRegistry;
		this.otelTracer = otelTracer;
	}

	@GetMapping(path = "/square")
	public Mono<ResponseEntity<Double>> getSquare(@RequestParam(value = "value") Double value) {
		return Mono.fromSupplier(() -> value)
				.doOnNext(aDouble -> {
					log.info("Calculating value {}", aDouble);
					log.info("Current Baggage = {}", otelTracer.getAllBaggage());
				})
				.filter(Objects::nonNull)
				.switchIfEmpty(Mono.error(new IllegalStateException("Incorrect value")))
				.map(this::computeSquare)
				.doOnNext(squareValue -> {
					getCurrentObservation()
							.highCardinalityKeyValue("value.received.from.front", String.valueOf(value))
							.highCardinalityKeyValue("value.sent.to.front", String.valueOf(squareValue));
					log.info("Respond result = {} to front", squareValue);
				})
				.map(ResponseEntity::ok)
				.name("getSquare-method")
				.tap(Micrometer.observation(observationRegistry));
	}

	private Double computeSquare(Double value) {
		return value * value;
	}

	private Observation getCurrentObservation() {
		return Optional.ofNullable(observationRegistry.getCurrentObservation())
				.orElseGet(() -> {
					log.warn("No current observation found");
					return Observation.NOOP;
				});
	}
}
