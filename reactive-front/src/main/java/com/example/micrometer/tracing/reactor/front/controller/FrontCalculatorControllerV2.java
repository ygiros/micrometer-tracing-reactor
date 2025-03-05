package com.example.micrometer.tracing.reactor.front.controller;

import io.micrometer.context.ContextRegistry;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.tracing.contextpropagation.ObservationAwareBaggageThreadLocalAccessor;
import io.micrometer.tracing.contextpropagation.reactor.ReactorBaggage;
import io.micrometer.tracing.otel.bridge.OtelTracer;
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
 * Web controller with spans creation + spans attributes assignations + context propagation
 */
@RestController
@RequestMapping("/v2/calculator")
@Slf4j
public class FrontCalculatorControllerV2 {

	public static final String DELEGATE_ENDPOINT = "/delegate/v2/calculator/square";
	private final ObservationRegistry observationRegistry;
	private final WebClient webClientToDelegate;
	private final OtelTracer otelTracer;

	public FrontCalculatorControllerV2(ObservationRegistry observationRegistry,
									   @Qualifier("webClientToDelegate") WebClient webClientToDelegate,
									   @Qualifier("micrometerOtelTracer") OtelTracer otelTracer) {
		this.observationRegistry = observationRegistry;
		this.webClientToDelegate = webClientToDelegate;
		this.otelTracer = otelTracer;

		// ##############
		// https://docs.micrometer.io/tracing/reference/configuring.html#_context_propagation_with_micrometer_tracing
		ObservationAwareBaggageThreadLocalAccessor observationAwareBaggageThreadLocalAccessor = new ObservationAwareBaggageThreadLocalAccessor(observationRegistry, otelTracer);
		ContextRegistry.getInstance()
				.registerThreadLocalAccessor(observationAwareBaggageThreadLocalAccessor);
		// ##############
	}

	@GetMapping(path = "/square")
	public Mono<ResponseEntity<Double>> getSquare(@RequestParam(value = "value") Double value) {

		return Mono.fromSupplier(() -> value)
				.doOnNext(aDouble -> {
					log.info("Receive request to calculate square of {}", aDouble);
					log.info("Current Baggage = {}", otelTracer.getAllBaggage());
				})
				.flatMap(this::computeSquare)
				.doOnNext(squareValue -> {
					log.info("Respond result = {} to client", squareValue);
					log.info("Current Baggage = {}", otelTracer.getAllBaggage());
				})
				.map(ResponseEntity::ok)
				// Name sequence (= name generated span)
				.name("getSquare-method")
				// Bind key/value pair to sequence (= set attribute to current span)
				.tag("value.from.request", value.toString())
				// Declare observation on sequence (= generate span)
				.tap(Micrometer.observation(observationRegistry))
				// Appends Baggage - appends here because of https://github.com/micrometer-metrics/tracing/issues/561
				// Didn't find the explanation of why it must be declared at the end ?
				.contextWrite(ReactorBaggage.append("baggage.value.from.request", String.valueOf(value)));
	}

	private Mono<Double> computeSquare(Double value) {
		log.info("Request delegate to calculate square of {}", value);

		return webClientToDelegate.get()
				.uri(uriBuilder ->
						uriBuilder
								.path(DELEGATE_ENDPOINT)
								.queryParam("value", value)
								.build())
				// Check context propagation in HTTP headers
				.httpRequest(clientHttpRequest -> log.info("Request headers to delegate : {}", clientHttpRequest.getHeaders()))
				.retrieve()
				.bodyToMono(Double.class)
				// Set attributes to current span with Observation API
				.doOnNext(squareValue ->
						getCurrentObservation()
								.highCardinalityKeyValue("value.sent.to.delegate", String.valueOf(value))
								.highCardinalityKeyValue("value.received.from.delegate", String.valueOf(squareValue)))
				.flatMap(this::addSquareValueInBaggage)
				.name("computeSquare-method")
				.tap(Micrometer.observation(observationRegistry));
	}

	private Mono<Double> addSquareValueInBaggage(Double squareValue) {
		return Mono.fromSupplier(() -> squareValue)
				.contextWrite(ReactorBaggage.append("baggage.result.from.delegate.sent.to.front", String.valueOf(squareValue)));
	}

	private Observation getCurrentObservation() {
		return Optional.ofNullable(observationRegistry.getCurrentObservation())
				.orElseGet(() -> {
					log.warn("No current observation found");
					return Observation.NOOP;
				});
	}
}
