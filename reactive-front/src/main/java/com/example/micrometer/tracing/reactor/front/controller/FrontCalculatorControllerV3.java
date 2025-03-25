package com.example.micrometer.tracing.reactor.front.controller;

import com.example.micrometer.tracing.reactor.front.service.TracingService;
import io.micrometer.tracing.contextpropagation.reactor.ReactorBaggage;
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

import java.util.Map;

/**
 * V3 : Factorizing ObservationRegistry & OtelTracer features in a TracingService
 */
@RestController
@RequestMapping("/v3/calculator")
@Slf4j
public class FrontCalculatorControllerV3 {

	public static final String DELEGATE_ENDPOINT = "/delegate/v2/calculator/square";
	private final WebClient webClientToDelegate;
	private final TracingService tracingService;

	public FrontCalculatorControllerV3(@Qualifier("webClientToDelegate") WebClient webClientToDelegate,
									   TracingService tracingService) {
		this.webClientToDelegate = webClientToDelegate;
		this.tracingService = tracingService;
	}

	/**
	 * Simplified endpoint to compute directly 2² without requesting ReactiveDelegateApplication
	 *
	 * @return 2² = 4
	 */
	@GetMapping(path = "/square-of-two")
	public Mono<ResponseEntity<Double>> getSquareOf2() {

		double value = 2.0;

		return Mono.fromSupplier(() -> value)
				.doOnNext(aDouble -> {
					log.info("1 - Current Baggage = {}", this.tracingService.getOtelTracer().getAllBaggage());
				})
				.map(aDouble -> aDouble * aDouble)
				.flatMap(squareValue ->
						Mono.just(squareValue)
								.contextWrite(ReactorBaggage.append("squareValue", String.valueOf(squareValue)))
				)
				.doOnNext(aDouble -> {
					log.info("2 - Current Baggage = {}", this.tracingService.getOtelTracer().getAllBaggage());
				})
				.map(ResponseEntity::ok)
				.name("get-square-of-two")
				.tap(Micrometer.observation(tracingService.getObservationRegistry()))
				.contextWrite(ReactorBaggage.append("value", String.valueOf(value)));
	}

	/**
	 * Endpoint to compute a square value.
	 * <br/>
	 * Compute is delegate to ReactiveDelegateApplication.
	 *
	 * @param value to compute the square of
	 * @return square value
	 */
	@GetMapping(path = "/square")
	public Mono<ResponseEntity<Double>> getSquare(@RequestParam(value = "value") Double value) {

		return Mono.fromSupplier(() -> value)
				.doOnNext(aDouble -> {
					log.info("Receive request to calculate square of {}", aDouble);
					tracingService.logCurrentBaggage();
				})
				.flatMap(this::computeSquare)
				.doOnNext(squareValue -> {
					log.info("Respond result = {} to client", squareValue);
					tracingService.logCurrentBaggage();
				})
				.map(ResponseEntity::ok)
				// Name sequence (= name generated span)
				// .name must be declared before .tap to correctly name observation
				.name("getSquare-method")
				// Bind key/value pair to sequence (= set attribute to current span)
				// Can be used to set attributes with values declared before sequence
				.tag("value.from.request", value.toString())
				// Declare observation on sequence (= generate span)
				.tap(Micrometer.observation(tracingService.getObservationRegistry()))
				// Check https://github.com/micrometer-metrics/tracing/issues/959#issuecomment-2706448262 for explanation on why .contextWrite() must be at the end of the sequence
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
				.doOnNext(squareValue ->
						// Set attributes to current span with Observation API
						// Can be used to set attributes from result of previous Reactor operator (squareValue here)
						tracingService.addAttributes(
								Map.of(
										"value.sent.to.delegate", value,
										"value.received.from.delegate", squareValue)))
				.flatMap(squareValue -> {
					// Add squareValue returned by HTTP request in Baggage
					return this.tracingService.addBaggage(squareValue, "baggage.value.received.from.delegate", String.valueOf(squareValue));
				})
				.name("computeSquare-method")
				.tap(Micrometer.observation(tracingService.getObservationRegistry()));
	}
}
