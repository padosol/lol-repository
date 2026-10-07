package com.mmrtr.lol.controller.support;

import com.mmrtr.lol.controller.CoreExceptionAdvice;
import com.mmrtr.lol.controller.support.response.ApiResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

/** 실제 Tomcat 으로 공개 포트·내부 포트 connector 와 경로 필터를 함께 검증한다. */
@SpringBootTest(
        classes = PublicApiPortIntegrationTest.TestApp.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PublicApiPortIntegrationTest {

    private static final int INTERNAL_PORT = freePort();

    private final HttpClient client = HttpClient.newHttpClient();

    @LocalServerPort
    private int publicPort;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("lol.web.internal-port", () -> INTERNAL_PORT);
    }

    @DisplayName("공개 포트: 소유 prefix 200, /internal 404, 매핑 없는 소유 prefix 경로 404")
    @Test
    void publicPort() throws Exception {
        assertThat(get(publicPort, "/api/v1/game-data/ping").statusCode()).isEqualTo(200);

        HttpResponse<String> internal = get(publicPort, "/internal/ping");
        assertThat(internal.statusCode()).isEqualTo(404);
        assertThat(internal.body()).contains("\"errorCode\":\"E404\"");

        HttpResponse<String> unmapped = get(publicPort, "/api/v1/game-data/unknown");
        assertThat(unmapped.statusCode()).isEqualTo(404);
        assertThat(unmapped.body()).contains("\"result\":\"ERROR\"");

        assertThat(get(publicPort, "/api/v1/members/me").statusCode()).isEqualTo(404);
    }

    @DisplayName("내부 포트: /internal 200, 공개 경로 404")
    @Test
    void internalPort() throws Exception {
        assertThat(get(INTERNAL_PORT, "/internal/ping").statusCode()).isEqualTo(200);
        assertThat(get(INTERNAL_PORT, "/api/v1/game-data/ping").statusCode()).isEqualTo(404);
    }

    private HttpResponse<String> get(int port, String path) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static int freePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import({PublicApiWebConfig.class, PublicApiExceptionAdvice.class, CoreExceptionAdvice.class,
            PublicPingController.class, InternalPingController.class})
    static class TestApp {
    }

    @PublicApi
    @RestController
    static class PublicPingController {
        @GetMapping("/api/v1/game-data/ping")
        public ApiResponse<String> ping() {
            return ApiResponse.success("pong");
        }
    }

    @RestController
    static class InternalPingController {
        @GetMapping("/internal/ping")
        public String ping() {
            return "pong";
        }
    }
}
