package com.crowded.core;

import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.otel.bridge.OtelCurrentTraceContext;
import io.micrometer.tracing.otel.bridge.OtelPropagator;
import io.micrometer.tracing.otel.bridge.OtelTracer;
import io.micrometer.tracing.propagation.Propagator;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.trace.SdkTracerProvider;

/** The tracer and W3C propagator Spring Boot configures, without exporting anything. */
public final class TestTracing {

  private static final OpenTelemetrySdk OTEL =
      OpenTelemetrySdk.builder()
          .setTracerProvider(SdkTracerProvider.builder().build())
          .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
          .build();

  private TestTracing() {}

  public static Tracer tracer() {
    return new OtelTracer(OTEL.getTracer("test"), new OtelCurrentTraceContext(), event -> {});
  }

  public static Propagator propagator() {
    return new OtelPropagator(OTEL.getPropagators(), OTEL.getTracer("test"));
  }
}
