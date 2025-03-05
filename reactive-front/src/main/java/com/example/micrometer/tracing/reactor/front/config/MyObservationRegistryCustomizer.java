package com.example.micrometer.tracing.reactor.front.config;

import io.micrometer.observation.ObservationRegistry;
import io.micrometer.tracing.otel.bridge.OtelTracer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.actuate.autoconfigure.observation.ObservationRegistryCustomizer;
import org.springframework.stereotype.Component;
import reactor.netty.observability.ReactorNettyTracingObservationHandler;

@Component
public class MyObservationRegistryCustomizer implements ObservationRegistryCustomizer<ObservationRegistry> {

	private final OtelTracer otelTracer;

	public MyObservationRegistryCustomizer(@Qualifier("micrometerOtelTracer") OtelTracer otelTracer) {
		this.otelTracer = otelTracer;
	}

	@Override
	public void customize(ObservationRegistry registry) {
		registry.observationConfig().observationHandler(new ReactorNettyTracingObservationHandler(otelTracer));
	}
}
