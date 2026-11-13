package com.mmrtr.lol.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.otel.bridge.OtelTracer;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.autoconfigure.observation.ObservationAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.opentelemetry.OpenTelemetryAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.tracing.MicrometerTracingAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.tracing.OpenTelemetryTracingAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.tracing.otlp.OtlpTracingAutoConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

/**
 * 분산 추적은 local 프로파일에서만 켠다. 실제 application.yml·application-local.yml 을 읽어, local 이면 trace 를 만들어
 * Tempo(OTLP)로 보내고 그 밖의 프로파일이면 tracer 자체를 만들지 않는지(로그에 traceId 도 붙지 않는지) 본다.
 * 자동 설정은 앱과 같이 spring.autoconfigure.exclude 를 따르도록 {@link ImportAutoConfiguration} 으로 올린다.
 */
class TracingProfileConfigTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withUserConfiguration(TracingAutoConfigurations.class);

    @Test
    @DisplayName("local 프로파일이면 trace 를 만들고 OTLP 로 Tempo 에 보낸다")
    void localProfile_tracesAndExportsOverOtlp() {
        runner.withPropertyValues("spring.profiles.active=local").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(OtelTracer.class);
            assertThat(context).hasSingleBean(OtlpHttpSpanExporter.class);
            assertThat(context.getEnvironment().getProperty("management.tracing.sampling.probability"))
                    .isEqualTo("1.0");
            assertThat(context.getEnvironment().getProperty("spring.rabbitmq.listener.simple.observation-enabled"))
                    .isEqualTo("true");
            assertThat(context.getEnvironment().getProperty("spring.rabbitmq.template.observation-enabled"))
                    .isEqualTo("true");
        });
    }

    @Test
    @DisplayName("프로파일이 없으면 tracer 를 만들지 않는다")
    void defaultProfile_noTracer() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(Tracer.class);
            assertThat(context).doesNotHaveBean(SdkTracerProvider.class);
            assertThat(context).doesNotHaveBean(OtlpHttpSpanExporter.class);
        });
    }

    @Test
    @DisplayName("prod 프로파일이면 tracer 를 만들지 않는다 (운영은 그대로)")
    void prodProfile_noTracer() {
        runner.withPropertyValues("spring.profiles.active=prod").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(Tracer.class);
            assertThat(context).doesNotHaveBean(SdkTracerProvider.class);
            assertThat(context).doesNotHaveBean(OtlpHttpSpanExporter.class);
        });
    }

    @Configuration(proxyBeanMethods = false)
    @ImportAutoConfiguration({
            ObservationAutoConfiguration.class,
            MicrometerTracingAutoConfiguration.class,
            OpenTelemetryAutoConfiguration.class,
            OpenTelemetryTracingAutoConfiguration.class,
            OtlpTracingAutoConfiguration.class})
    static class TracingAutoConfigurations {
    }
}
