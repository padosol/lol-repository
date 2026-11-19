package com.mmrtr.lol.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.otel.bridge.OtelTracer;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.autoconfigure.metrics.MetricsAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.metrics.export.simple.SimpleMetricsExportAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.observation.ObservationAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.opentelemetry.OpenTelemetryAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.tracing.MicrometerTracingAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.tracing.NoopTracerAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.tracing.OpenTelemetryTracingAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.tracing.otlp.OtlpTracingAutoConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Configuration;

/**
 * 분산 추적은 local 프로파일에서만 켠다. 실제 application.yml·application-local.yml 을 읽어, local 이면 trace 를 만들어
 * Tempo(OTLP)로 보내고 그 밖의 프로파일이면 tracer 자체를 만들지 않는지(로그에 traceId 도 붙지 않는지) 본다.
 * 자동 설정은 앱과 같이 spring.autoconfigure.exclude 를 따르도록 {@link ImportAutoConfiguration} 으로 올린다.
 * 앱에 실제로 뜨는 Noop tracer·지표 자동 설정도 같이 올린다. 빠뜨리면 tracer 없이 지표 handler 만 남는 조합
 * (관측을 닫을 때 TracingContext 를 찾다 실패한다)을 못 본다.
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
    @DisplayName("프로파일이 없으면 tracer 를 만들지 않고 관측은 지표로만 남는다")
    void defaultProfile_noTracer() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(Tracer.class);
            assertThat(context).doesNotHaveBean(SdkTracerProvider.class);
            assertThat(context).doesNotHaveBean(OtlpHttpSpanExporter.class);
            assertObservationRecordsTimer(context);
        });
    }

    @Test
    @DisplayName("prod 프로파일이면 tracer 를 만들지 않고 관측은 지표로만 남는다 (운영은 그대로)")
    void prodProfile_noTracer() {
        runner.withPropertyValues("spring.profiles.active=prod").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(Tracer.class);
            assertThat(context).doesNotHaveBean(SdkTracerProvider.class);
            assertThat(context).doesNotHaveBean(OtlpHttpSpanExporter.class);
            assertObservationRecordsTimer(context);
        });
    }

    /** @Scheduled·HTTP 요청처럼 관측 하나를 열고 닫아, 예외 없이 끝나고 지표 handler 가 timer 를 남기는지 본다. */
    private static void assertObservationRecordsTimer(ApplicationContext context) {
        ObservationRegistry observationRegistry = context.getBean(ObservationRegistry.class);
        Observation.createNotStarted("tracing.profile.check", observationRegistry).observe(() -> { });
        assertThat(context.getBean(MeterRegistry.class).find("tracing.profile.check").timer())
                .isNotNull()
                .satisfies(timer -> assertThat(timer.count()).isEqualTo(1));
    }

    @Configuration(proxyBeanMethods = false)
    @ImportAutoConfiguration({
            MetricsAutoConfiguration.class,
            SimpleMetricsExportAutoConfiguration.class,
            ObservationAutoConfiguration.class,
            NoopTracerAutoConfiguration.class,
            MicrometerTracingAutoConfiguration.class,
            OpenTelemetryAutoConfiguration.class,
            OpenTelemetryTracingAutoConfiguration.class,
            OtlpTracingAutoConfiguration.class})
    static class TracingAutoConfigurations {
    }
}
