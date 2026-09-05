package kr.youthpolicymate.ingestion;

import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

/** 서울 필터의 지정 페이지를 한 번 요청한다. 오류에 요청 URL이나 인증키를 포함하지 않는다. */
public class OntongApiClient implements AutoCloseable {
    static final int MAX_BYTES = 1024 * 1024;
    private final HttpClient client;
    private final ObjectMapper mapper;
    private final Clock clock;
    private final URI endpoint;

    public OntongApiClient(ObjectMapper mapper, Clock clock) {
        this(mapper, clock, URI.create("https://www.youthcenter.go.kr/go/ythip/getPlcy"));
    }

    OntongApiClient(ObjectMapper mapper, Clock clock, URI endpoint) {
        this.mapper = mapper;
        this.clock = clock;
        this.endpoint = endpoint;
        client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NEVER).build();
    }

    public Response fetch(String apiKey, int page) {
        if (apiKey == null || apiKey.isBlank()) throw new Failure("API_KEY_MISSING");
        if (page < 1 || page > 1000) throw new Failure("INVALID_PAGE");
        var encodedKey = URLEncoder.encode(apiKey, StandardCharsets.UTF_8);
        try {
            var uri = URI.create(endpoint + "?apiKeyNm=" + encodedKey
                    + "&pageNum=" + page + "&pageSize=10&pageType=1&rtnType=json&zipCd=11000");
            var request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(20))
                    .header("Accept", "application/json").GET().build();
            var future = client.sendAsync(request, info -> HttpResponse.BodySubscribers.limiting(
                    HttpResponse.BodySubscribers.ofByteArray(), MAX_BYTES));
            HttpResponse<byte[]> response;
            try { response = future.get(20, TimeUnit.SECONDS); }
            finally { if (!future.isDone()) future.cancel(true); }
            if (response.statusCode() != 200) throw new Failure("HTTP_" + response.statusCode());
            if (!response.headers().firstValue("Content-Type").orElse("").toLowerCase(java.util.Locale.ROOT)
                    .contains("json")) throw new Failure("NON_JSON_RESPONSE");
            var raw = new String(response.body(), StandardCharsets.UTF_8);
            // JSON 유니코드 이스케이프로 돌아온 인증키도 저장 전에 검사한다.
            if (raw.contains(apiKey) || raw.contains(encodedKey)) throw new Failure("SECRET_IN_RESPONSE");
            var decoded = mapper.readTree(raw).toString();
            if (decoded.contains(apiKey) || decoded.contains(encodedKey)) throw new Failure("SECRET_IN_RESPONSE");
            return new Response(clock.instant(), raw);
        } catch (Failure failure) { throw failure; }
        catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new Failure("REQUEST_INTERRUPTED");
        } catch (Exception exception) {
            // 외부 예외 원인에는 인증키가 든 URI나 응답 일부가 들어갈 수 있다.
            throw new Failure("REQUEST_OR_RESPONSE_FAILED");
        }
    }

    @Override public void close() { client.shutdownNow(); }
    public record Response(Instant receivedAt, String rawBody) {}
    public static final class Failure extends RuntimeException {
        public Failure(String code) { super(code); }
    }
}
