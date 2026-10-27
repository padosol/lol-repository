package com.mmrtr.lol;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.util.ContextInitializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.logging.LoggingInitializationContext;
import org.springframework.boot.logging.logback.LogbackLoggingSystem;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.env.MockEnvironment;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * logback-spring.xml 의 프로파일별 콘솔 포맷. 운영(prod)은 Loki 가 줄 단위로 수집하므로 스택트레이스까지 한 줄
 * JSON 이어야 하고, 최상위 {@code level} 로 detected_level 을 잡도록 평평해야 한다. 그 외 프로파일은 기존 패턴을
 * 유지한다. 전체 컨텍스트 없이 실제 설정 파일로 로깅만 초기화한다.
 */
@ExtendWith(OutputCaptureExtension.class)
class LogbackConsoleFormatTest {

    private static final String CONFIG_LOCATION = "classpath:logback-spring.xml";

    private final LogbackLoggingSystem loggingSystem = new LogbackLoggingSystem(getClass().getClassLoader());
    private final Logger log = LoggerFactory.getLogger(LogbackConsoleFormatTest.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private Set<Object> systemPropertyNames;

    @BeforeEach
    void setUp() {
        systemPropertyNames = new HashSet<>(System.getProperties().keySet());
    }

    @AfterEach
    void tearDown() throws Exception {
        MDC.clear();
        System.getProperties().keySet().retainAll(systemPropertyNames);
        loggingSystem.cleanUp();
        LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
        loggerContext.reset();
        new ContextInitializer(loggerContext).autoConfig();
    }

    @DisplayName("prod 프로파일은 MDC·예외를 포함해 콘솔 로그를 평평한 한 줄 JSON 으로 출력한다")
    @Test
    void prodProfileWritesJson(CapturedOutput output) throws Exception {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        new YamlPropertySourceLoader()
                .load("application-prod", new ClassPathResource("application-prod.yml"))
                .forEach(environment.getPropertySources()::addFirst);
        initialize(environment);

        MDC.put("traceId", "abcd1234");
        log.info("json-info");
        log.error("json-error", new IllegalStateException("boom"));

        List<String> lines = output.getOut().lines().filter(line -> !line.isBlank()).toList();
        assertThat(lines).hasSize(2);
        JsonNode info = objectMapper.readTree(lines.get(0));
        JsonNode error = objectMapper.readTree(lines.get(1));
        assertThat(info.path("message").asText()).isEqualTo("json-info");
        assertThat(info.path("level").asText()).isEqualTo("INFO");
        assertThat(info.path("logger_name").asText()).isEqualTo(LogbackConsoleFormatTest.class.getName());
        assertThat(info.path("traceId").asText()).isEqualTo("abcd1234");
        assertThat(info.has("@version")).isFalse();
        assertThat(info.has("level_value")).isFalse();
        assertThat(error.path("level").asText()).isEqualTo("ERROR");
        assertThat(error.path("stack_trace").asText()).contains("IllegalStateException: boom");
        assertThat(List.of(info, error))
                .flatMap(JsonNode::properties)
                .allSatisfy(field -> assertThat(field.getValue().isValueNode()).as(field.getKey()).isTrue());
    }

    @DisplayName("prod 가 아닌 프로파일은 기존 패턴으로 출력한다")
    @Test
    void nonProdProfileWritesPattern(CapturedOutput output) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("local");
        initialize(environment);

        MDC.put("traceId", "abcd1234");
        log.info("pattern-info");

        assertThat(output.getOut())
                .doesNotContain("{")
                .containsPattern("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\.\\d{3}  INFO \\[abcd1234] --- ")
                .contains(": pattern-info");
    }

    private void initialize(MockEnvironment environment) {
        loggingSystem.beforeInitialize();
        loggingSystem.initialize(new LoggingInitializationContext(environment), CONFIG_LOCATION, null);
    }
}
