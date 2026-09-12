package kr.youthpolicymate.ingestion;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.Currency;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** 운영 조회 명령에서만 사용한다. 생성·예산 정산·DB 연결은 하지 않는다. */
final class OpenAiCostsClient {
    private final RestClient client;
    private final String project;

    OpenAiCostsClient(String adminKey, String project, RestClient.Builder builder) {
        if (adminKey == null || adminKey.isBlank() || project == null || project.isBlank())
            throw new Unavailable("OpenAI 관리자 키와 조회할 프로젝트 ID를 설정해주세요.");
        this.project = project;
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build());
        factory.setReadTimeout(Duration.ofSeconds(10));
        client = builder.requestFactory(factory).defaultHeader("Authorization", "Bearer " + adminKey)
                .defaultHeader("User-Agent", "youth-policy-mate/1.0").build();
    }

    Report month(YearMonth month, Instant checkedAt) {
        var start = month.atDay(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        var next = month.plusMonths(1).atDay(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        var end = checkedAt.isBefore(next) ? checkedAt : next;
        if (start.getEpochSecond() >= end.getEpochSecond()) throw new Unavailable("조회 월은 현재 UTC 월 또는 이전 월로 지정해주세요.");
        var totals = new HashMap<String, BigDecimal>();
        var cursors = new HashSet<String>();
        var buckets = new HashSet<Long>();
        String cursor = null;
        // 일 단위 월 집계는 최대 31개 버킷이다. 잘못된 페이지 응답으로 무한 조회하지 않는다.
        for (int page = 0; page < month.lengthOfMonth(); page++) {
            var response = fetch(start, end, cursor);
            if (response == null || response.data() == null || response.hasMore() == null) throw incomplete();
            for (var bucket : response.data()) {
                if (bucket == null || bucket.startTime() == null || bucket.startTime() < start.getEpochSecond()
                        || bucket.startTime() >= end.getEpochSecond() || !buckets.add(bucket.startTime()) || bucket.results() == null) throw incomplete();
                for (var result : bucket.results()) {
                    if (result == null || !project.equals(result.projectId()) || result.amount() == null
                            || result.amount().value() == null || result.amount().currency() == null) throw incomplete();
                    String currency;
                    try { currency = Currency.getInstance(result.amount().currency().toUpperCase(Locale.ROOT)).getCurrencyCode(); }
                    catch (IllegalArgumentException invalid) { throw incomplete(); }
                    totals.merge(currency, result.amount().value(), BigDecimal::add);
                }
            }
            if (!response.hasMore()) return new Report(project, start, end, checkedAt, Map.copyOf(totals));
            cursor = response.nextPage();
            if (cursor == null || cursor.isBlank() || !cursors.add(cursor)) throw incomplete();
        }
        throw incomplete();
    }

    private Page fetch(Instant start, Instant end, String cursor) {
        try {
            return client.get().uri(builder -> {
                var uri = builder.path("/organization/costs").queryParam("start_time", start.getEpochSecond())
                        .queryParam("end_time", end.getEpochSecond()).queryParam("bucket_width", "1d")
                        .queryParam("limit", 31).queryParam("project_ids[]", "{project}").queryParam("group_by[]", "project_id");
                if (cursor != null) uri.queryParam("page", "{cursor}");
                return uri.build(cursor == null ? Map.of("project", project) : Map.of("project", project, "cursor", cursor));
            }).retrieve().body(Page.class);
        } catch (RestClientResponseException failure) {
            throw new Unavailable(switch (failure.getStatusCode().value()) {
                case 401, 403 -> "OpenAI 비용 조회 권한을 확인해주세요.";
                case 429 -> "OpenAI 조회 한도에 도달했습니다. 잠시 후 다시 실행해주세요.";
                default -> "OpenAI 비용을 조회하지 못했습니다. 연결과 공급자 상태를 확인해주세요.";
            });
        } catch (RestClientException failure) { throw new Unavailable("OpenAI 비용 응답을 확인하지 못했습니다. 잠시 후 다시 실행해주세요."); }
    }

    private static Unavailable incomplete() { return new Unavailable("OpenAI 비용 응답이 불완전해 합계를 표시하지 않습니다."); }
    static final class Unavailable extends RuntimeException {
        Unavailable(String message) { super(message); }
    }
    record Report(String project, Instant start, Instant end, Instant checkedAt, Map<String, BigDecimal> totals) {}
    private record Page(List<Bucket> data, @JsonProperty("has_more") Boolean hasMore, @JsonProperty("next_page") String nextPage) {}
    private record Bucket(@JsonProperty("start_time") Long startTime, List<Result> results) {}
    private record Result(@JsonProperty("project_id") String projectId, Amount amount) {}
    private record Amount(BigDecimal value, String currency) {}
}
