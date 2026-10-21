package com.mmrtr.lol.controller.internal;

import com.mmrtr.lol.controller.CoreExceptionAdvice;
import com.mmrtr.lol.controller.support.InternalApiDocsConfig;
import com.mmrtr.lol.controller.support.PublicApiExceptionAdvice;
import com.mmrtr.lol.controller.support.PublicApiWebConfig;
import com.mmrtr.lol.domain.summoner.application.usecase.DuoProfileQueryUseCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 내부 API OpenAPI 계약 (서비스 분리 설계 5절). 실제 Tomcat 의 내부 포트에서 받은 문서가 저장소의 계약 파일과 같아야 한다.
 * 생성 결과는 항상 {@code build/openapi/} 에 남기므로, 계약을 의도적으로 바꿀 때는 그 파일로 계약 파일을 덮어쓴다.
 */
@SpringBootTest(
        classes = InternalApiContractTest.TestApp.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class InternalApiContractTest {

    private static final String CONTRACT_FILE = "internal-v1.openapi.yaml";
    private static final String API_DOCS_PATH = "/internal/v1/api-docs.yaml";
    private static final int INTERNAL_PORT = freePort();

    private final HttpClient client = HttpClient.newHttpClient();

    @LocalServerPort
    private int publicPort;

    @MockitoBean
    private DuoProfileQueryUseCase duoProfileQueryUseCase;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("lol.web.internal-port", () -> INTERNAL_PORT);
    }

    @DisplayName("내부 포트의 OpenAPI 문서는 docs/api 계약 파일과 같다")
    @Test
    void apiDocs_internalPort_matchesCommittedContract() throws Exception {
        // when
        HttpResponse<String> response = get(INTERNAL_PORT, API_DOCS_PATH);
        Path generated = Path.of("build", "openapi", CONTRACT_FILE);
        Files.createDirectories(generated.getParent());
        Files.writeString(generated, response.body());

        // then
        Path contract = repositoryRoot().resolve(Path.of("docs", "api", CONTRACT_FILE));
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(normalize(response.body()))
                .as("계약이 바뀌었다면 %s 로 %s 를 갱신한다", generated.toAbsolutePath(), contract)
                .isEqualTo(normalize(Files.readString(contract)));
    }

    @DisplayName("OpenAPI 문서는 공개 포트에 노출하지 않는다")
    @Test
    void apiDocs_publicPort_returns404() throws Exception {
        // when & then
        assertThat(get(publicPort, API_DOCS_PATH).statusCode()).isEqualTo(404);
    }

    private HttpResponse<String> get(int port, String path) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static String normalize(String yaml) {
        return yaml.replace("\r\n", "\n").strip();
    }

    private static Path repositoryRoot() {
        Path dir = Path.of("").toAbsolutePath();
        while (!Files.exists(dir.resolve("settings.gradle"))) {
            dir = dir.getParent();
        }
        return dir;
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
    @Import({PublicApiWebConfig.class, InternalApiDocsConfig.class, PublicApiExceptionAdvice.class,
            CoreExceptionAdvice.class, DuoProfileInternalController.class})
    static class TestApp {
    }
}
