package kr.youthpolicymate.ingestion;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.IntFunction;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.*;

class OpenAiCostsTest {
    private static final Instant NOW = Instant.parse("2026-09-12T14:00:00Z");
    private static final long FIRST = Instant.parse("2026-08-01T00:00:00Z").getEpochSecond();
    private static final String KEY = "fixture-admin-key";
    private static final String PROJECT = "proj_fixture";

    @Test @DisplayName("관리자 키로 지정 프로젝트의 모든 비용 페이지를 읽고 통화별 소수 합계를 유지한다")
    void readsAllPagesAndPreservesCurrency() throws Exception {
        try (var server = new Provider(index -> new Reply(200, page(FIRST + (index - 1L) * 86400,
                index == 1 ? row(PROJECT, "0.100000000000000001", "usd") + "," + row(PROJECT, "0.2", "eur")
                        : row(PROJECT, "0.2", "usd") + "," + row(PROJECT, "-0.01", "usd"),
                index == 1, index == 1 ? "opaque+/=&value" : null)))) {
            var report = server.client(KEY, PROJECT).month(YearMonth.of(2026, 8), NOW);
            assertThat(report.totals()).containsEntry("USD", new BigDecimal("0.290000000000000001"))
                    .containsEntry("EUR", new BigDecimal("0.2"));
            assertThat(report.start()).isEqualTo(Instant.ofEpochSecond(FIRST));
            assertThat(report.end()).isEqualTo(Instant.parse("2026-09-01T00:00:00Z"));
            assertThat(report.checkedAt()).isEqualTo(NOW);
            assertThat(server.requests).hasSize(2).allSatisfy(request -> {
                assertThat(request.method()).isEqualTo("GET");
                assertThat(request.path()).isEqualTo("/v1/organization/costs");
                assertThat(request.authorization()).isEqualTo("Bearer " + KEY);
                assertThat(request.query()).containsEntry("project_ids[]", PROJECT).containsEntry("group_by[]", "project_id")
                        .containsEntry("start_time", Long.toString(FIRST)).containsEntry("end_time", "1788220800")
                        .containsEntry("bucket_width", "1d").containsEntry("limit", "31");
            });
            assertThat(server.requests.getFirst().query()).doesNotContainKey("page");
            assertThat(server.requests.getLast().query()).containsEntry("page", "opaque+/=&value");
            server.requests.clear();
            var command = run(server, "2026-08", Map.of("OPENAI_ADMIN_KEY", KEY, "OPENAI_COSTS_PROJECT_ID", PROJECT));
            assertThat(command.exit()).isZero();
            assertThat(command.output()).contains(PROJECT, "USD 0.290000000000000001", "EUR 0.2", "UTC", NOW.toString()).doesNotContain(KEY);
        }
    }

    @Test @DisplayName("월 경계는 UTC로 계산하며 현재 월은 조회 시각까지만 요청하고 미래 월은 호출하지 않는다")
    void usesUtcMonthAndClipsCurrentMonth() throws Exception {
        try (var server = new Provider(index -> new Reply(200, "{\"data\":[],\"has_more\":false}"))) {
            var client = server.client(KEY, PROJECT);
            assertThat(client.month(YearMonth.of(2026, 9), NOW).end()).isEqualTo(NOW);
            var leap = client.month(YearMonth.of(2024, 2), NOW);
            assertThat(leap.start()).isEqualTo(Instant.parse("2024-02-01T00:00:00Z"));
            assertThat(leap.end()).isEqualTo(Instant.parse("2024-03-01T00:00:00Z"));
            assertThatThrownBy(() -> client.month(YearMonth.of(2026, 9), Instant.parse("2026-08-31T15:01:00Z")))
                    .isInstanceOf(OpenAiCostsClient.Unavailable.class);
            assertThat(server.requests).hasSize(2);
            assertThat(server.requests.getFirst().query()).containsEntry("end_time", Long.toString(NOW.getEpochSecond()));
        }
    }

    @Test @DisplayName("누락 금액·잘못된 프로젝트·잘못된 통화·중복 날짜·끊긴 페이지를 부분 합계로 반환하지 않는다")
    void rejectsIncompleteResults() throws Exception {
        for (String body : List.of("{}", "{\"data\":[],\"has_more\":true}",
                page(FIRST, "{\"project_id\":\"proj_fixture\",\"amount\":{\"currency\":\"usd\"}}", false, null),
                page(FIRST, row("proj_other", "20", "usd"), false, null),
                page(FIRST, row(PROJECT, "20", "private-invalid-currency"), false, null),
                page(FIRST - 86400, row(PROJECT, "20", "usd"), false, null),
                "not-json-private-content")) {
            try (var server = new Provider(index -> new Reply(200, body))) {
                assertThatThrownBy(() -> server.client(KEY, PROJECT).month(YearMonth.of(2026, 8), NOW))
                        .isInstanceOf(OpenAiCostsClient.Unavailable.class).hasNoCause()
                        .hasMessageNotContaining("private").hasMessageNotContaining(KEY).hasMessageNotContaining("proj_other");
                assertThat(server.requests).hasSize(1);
            }
        }
        for (boolean duplicateBucket : List.of(true, false)) {
            try (var server = new Provider(index -> new Reply(200, page(FIRST + (duplicateBucket ? 0 : index - 1L) * 86400,
                    row(PROJECT, "1", "usd"), true, "same-cursor")))) {
                assertThatThrownBy(() -> server.client(KEY, PROJECT).month(YearMonth.of(2026, 8), NOW))
                        .isInstanceOf(OpenAiCostsClient.Unavailable.class);
                assertThat(server.requests).hasSize(2);
            }
        }
    }

    @Test @DisplayName("인증 오류·요청 한도·서버 오류는 재시도하지 않으며 다음 페이지 실패 시 앞선 합계를 출력하지 않는다")
    void doesNotRetryOrPrintPartialTotals() throws Exception {
        for (int status : List.of(401, 403, 429, 500)) {
            try (var server = new Provider(index -> index == 1
                    ? new Reply(200, page(FIRST, row(PROJECT, "123.456", "usd"), true, "next"))
                    : new Reply(status, "{\"error\":\"private-provider-error\"}"))) {
                var result = run(server, "2026-08", Map.of("OPENAI_ADMIN_KEY", KEY, "OPENAI_COSTS_PROJECT_ID", PROJECT));
                assertThat(result.exit()).isEqualTo(1);
                assertThat(result.output()).isEmpty();
                assertThat(result.error()).doesNotContain("123.456", "private", KEY, "Exception");
                assertThat(result.error()).contains(status == 429 ? "조회 한도" : status == 401 || status == 403 ? "조회 권한" : "조회하지 못했습니다");
                assertThat(server.requests).hasSize(2);
            }
        }
    }

    @Test @DisplayName("생성 키나 AI 활성화 설정을 대신 사용하지 않으며 비용 없음과 조회 실패를 구분한다")
    void isolatesCommandConfigurationAndOutput() throws Exception {
        try (var server = new Provider(index -> new Reply(200, "{\"data\":[],\"has_more\":false}"))) {
            for (var env : List.of(Map.of("OPENAI_API_KEY", "generation-key", "OPENAI_COSTS_PROJECT_ID", PROJECT), Map.of("OPENAI_ADMIN_KEY", KEY))) {
                var result = run(server, "2026-08", env);
                assertThat(result.exit()).isEqualTo(1);
                assertThat(result.error()).contains("관리자 키", "프로젝트 ID");
            }
            for (String month : List.of("invalid-secret", "2026-13", "0000-01", "2026-10")) {
                var result = run(server, month, Map.of("OPENAI_ADMIN_KEY", KEY, "OPENAI_COSTS_PROJECT_ID", PROJECT));
                assertThat(result.exit()).isEqualTo(1);
                assertThat(result.output()).isEmpty();
                assertThat(result.error()).doesNotContain("invalid-secret", KEY);
            }
            assertThat(server.requests).isEmpty();
            var result = run(server, "2026-08", Map.of("OPENAI_ADMIN_KEY", KEY, "OPENAI_COSTS_PROJECT_ID", PROJECT,
                    "AI_ENABLED", "true", "OPENAI_API_KEY", "generation-key", "DB_HOST", "unreachable"));
            assertThat(result.exit()).isZero();
            assertThat(result.output()).contains("공급자가 반환한 비용 항목이 없습니다", "최종 청구", "자동 적용하지 않습니다")
                    .doesNotContain("USD 0", KEY, "generation-key");
            assertThat(result.error()).isEmpty();
            assertThat(server.requests).hasSize(1);
        }
    }

    private static CommandResult run(Provider server, String month, Map<String, String> environment) {
        var output = new ByteArrayOutputStream(); var error = new ByteArrayOutputStream();
        int exit = OpenAiCostsCommand.run(new String[]{month}, environment, server.builder(), Clock.fixed(NOW, ZoneOffset.UTC),
                new PrintStream(output, true, StandardCharsets.UTF_8), new PrintStream(error, true, StandardCharsets.UTF_8));
        return new CommandResult(exit, output.toString(StandardCharsets.UTF_8), error.toString(StandardCharsets.UTF_8));
    }
    private static String row(String project, String value, String currency) {
        return "{\"project_id\":\"" + project + "\",\"amount\":{\"value\":" + value + ",\"currency\":\"" + currency + "\"}}";
    }
    private static String page(long start, String results, boolean more, String cursor) {
        return "{\"data\":[{\"start_time\":" + start + ",\"results\":[" + results + "]}],\"has_more\":" + more
                + ",\"next_page\":" + (cursor == null ? "null" : "\"" + cursor + "\"") + "}";
    }
    private record CommandResult(int exit, String output, String error) {}
    private record Reply(int status, String body) {}
    private record Request(String method, String path, String authorization, Map<String, String> query) {}
    private static final class Provider implements AutoCloseable {
        final HttpServer server;
        final List<Request> requests = new CopyOnWriteArrayList<>();
        Provider(IntFunction<Reply> response) throws Exception {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/v1/organization/costs", exchange -> {
                var query = java.util.Arrays.stream(exchange.getRequestURI().getRawQuery().split("&"))
                        .map(value -> value.split("=", 2)).collect(Collectors.toMap(value -> decode(value[0]), value -> decode(value[1])));
                requests.add(new Request(exchange.getRequestMethod(), exchange.getRequestURI().getPath(), exchange.getRequestHeaders().getFirst("Authorization"), query));
                var reply = response.apply(requests.size());
                byte[] bytes = reply.body().getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(reply.status(), bytes.length);
                exchange.getResponseBody().write(bytes); exchange.close();
            });
            server.start();
        }
        private static String decode(String value) { return URLDecoder.decode(value, StandardCharsets.UTF_8); }
        RestClient.Builder builder() { return RestClient.builder().baseUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/v1"); }
        OpenAiCostsClient client(String key, String project) { return new OpenAiCostsClient(key, project, builder()); }
        public void close() { server.stop(0); }
    }
}
