package com.example.micrometer.tracing.reactor.front.service;

import io.micrometer.context.ContextRegistry;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.tracing.contextpropagation.ObservationAwareBaggageThreadLocalAccessor;
import io.micrometer.tracing.contextpropagation.reactor.ReactorBaggage;
import io.micrometer.tracing.otel.bridge.OtelTracer;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.Optional;

@Service
@Getter
@Slf4j
public class TracingService {

	private final ObservationRegistry observationRegistry;
	private final OtelTracer otelTracer;

	public TracingService(ObservationRegistry observationRegistry,
						  OtelTracer otelTracer) {
		this.observationRegistry = observationRegistry;
		this.otelTracer = otelTracer;

		// ##############
		// https://docs.micrometer.io/tracing/reference/configuring.html#_context_propagation_with_micrometer_tracing
		ObservationAwareBaggageThreadLocalAccessor observationAwareBaggageThreadLocalAccessor = new ObservationAwareBaggageThreadLocalAccessor(observationRegistry, otelTracer);
		ContextRegistry.getInstance()
				.registerThreadLocalAccessor(observationAwareBaggageThreadLocalAccessor);
		// ##############
	}

	public void addAttribute(String key, String value) {
		this.getCurrentObservation().highCardinalityKeyValue(key, value);
	}

	public void addAttributes(Map<String, Object> attributes) {
		attributes.forEach((s, o) -> this.addAttribute(s, String.valueOf(o)));
	}

	public <T> Mono<T> addBaggage(T valueToReturn, String key, String value) {
		return Mono.just(valueToReturn)
				.contextWrite(ReactorBaggage.append(key, value));
	}

	public <T> Mono<T> addBaggage(T valueToReturn, Map<String, String> baggage) {
		return Mono.just(valueToReturn)
				.contextWrite(ReactorBaggage.append(baggage));
	}

	public Observation getCurrentObservation() {
		return Optional.ofNullable(this.observationRegistry.getCurrentObservation())
				.orElseGet(() -> {
					log.warn("No current observation found");
					return Observation.NOOP;
				});
	}

	public void logCurrentBaggage() {
		log.info("Current Baggage = {}", this.otelTracer.getAllBaggage());
	}
}
