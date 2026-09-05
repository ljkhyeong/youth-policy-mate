package kr.youthpolicymate.ingestion;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;

class OntongApiClientTest {
    private HttpServer server;
    private OntongApiClient client;
    private final AtomicInteger calls = new AtomicInteger();
    private final AtomicReference<String> query = new AtomicReference<>();
    private String response = "{\"resultCode\":200}";
    private int status = 200;

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/policy", exchange -> {
            calls.incrementAndGet();
            query.set(exchange.getRequestURI().getRawQuery());
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.getResponseHeaders().set("Location", "/policy");
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            try (var output = exchange.getResponseBody()) { output.write(bytes); }
        });
        server.start();
        client = new OntongApiClient(JsonMapper.builder().build(), Clock.fixed(Instant.EPOCH, ZoneOffset.UTC),
                URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/policy"));
    }

    @AfterEach void close() { client.close(); server.stop(0); }

    @Test
    @DisplayName("서울 필터의 지정 페이지를 최대 10건으로 한 번만 요청하고 원문을 보존한다")
    void requestsOnePage() {
        var result = client.fetch("test-key+/=", 2);
        assertThat(result.rawBody()).isEqualTo(response);
        assertThat(result.receivedAt()).isEqualTo(Instant.EPOCH);
        assertThat(query.get()).contains("apiKeyNm=test-key%2B%2F%3D", "pageNum=2", "pageSize=10", "zipCd=11000");
        assertThat(calls).hasValue(1);
    }

    @Test
    @DisplayName("리다이렉트와 호출 제한 응답은 따라가거나 재시도하지 않는다")
    void doesNotFollowOrRetry() {
        for (int code : new int[]{302, 429}) {
            status = code;
            assertThatThrownBy(() -> client.fetch("test-secret", 1)).hasMessage("HTTP_" + code).hasNoCause();
        }
        assertThat(calls).hasValue(2);
    }

    @Test
    @DisplayName("큰 응답과 깨진 JSON 및 인증키를 되돌려주는 응답은 원문을 반환하지 않는다")
    void rejectsUnsafeResponses() {
        response = "x".repeat(OntongApiClient.MAX_BYTES + 1);
        assertThatThrownBy(() -> client.fetch("test-secret", 1)).hasMessage("REQUEST_OR_RESPONSE_FAILED").hasNoCause();
        response = "not-json";
        assertThatThrownBy(() -> client.fetch("test-secret", 1)).hasMessage("REQUEST_OR_RESPONSE_FAILED").hasNoCause();
        response = "{\"message\":\"test-\\u0073ecret\"}";
        assertThatThrownBy(() -> client.fetch("test-secret", 1)).hasMessage("SECRET_IN_RESPONSE").hasNoCause();
        assertThat(calls).hasValue(3);
    }

    @Test
    @DisplayName("인증키 누락과 잘못된 페이지는 외부 요청 전에 차단한다")
    void rejectsMissingConfiguration() {
        assertThatThrownBy(() -> client.fetch("", 1)).hasMessage("API_KEY_MISSING");
        assertThatThrownBy(() -> client.fetch("test-secret", 0)).hasMessage("INVALID_PAGE");
        assertThat(calls).hasValue(0);
    }
}
