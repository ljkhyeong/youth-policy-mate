package kr.youthpolicymate.ingestion;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriUtils;
import tools.jackson.databind.ObjectMapper;

import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;

/** 서울 필터의 지정 페이지를 한 번 요청한다. 오류에 요청 URL이나 인증키를 포함하지 않는다. */
public class OntongApiClient {
    static final int MAX_BYTES = 1024 * 1024;
    private final RestClient client;
    private final ObjectMapper mapper;
    private final Clock clock;

    public OntongApiClient(ObjectMapper mapper, Clock clock) {
        this(mapper, clock, "https://www.youthcenter.go.kr/go/ythip/getPlcy");
    }

    OntongApiClient(ObjectMapper mapper, Clock clock, String endpoint) {
        this.mapper = mapper;
        this.clock = clock;
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NEVER).build());
        // Spring은 응답 헤더 대기와 본문 읽기를 합쳐 이 시간 안에 끝나지 않으면 요청을 끊는다.
        factory.setReadTimeout(Duration.ofSeconds(20));
        // Boot가 구성한 빌더의 관측은 인증키가 든 전체 주소를 기록하므로 관측이 없는 정적 빌더를 쓴다.
        client = RestClient.builder().requestFactory(factory).baseUrl(endpoint).build();
    }

    public Response fetch(String apiKey, int page) {
        if (apiKey == null || apiKey.isBlank()) throw new Failure("API_KEY_MISSING");
        if (page < 1 || page > 1000) throw new Failure("INVALID_PAGE");
        // URI 변수와 같은 방식으로 인코딩해 실제로 보낸 형태의 반사도 찾는다.
        var encodedKey = UriUtils.encode(apiKey, StandardCharsets.UTF_8);
        try {
            var raw = client.get()
                    .uri(uri -> uri.queryParam("apiKeyNm", "{key}").queryParam("pageNum", page).queryParam("pageSize", 10)
                            .queryParam("pageType", 1).queryParam("rtnType", "json").queryParam("zipCd", "11000").build(apiKey))
                    .accept(MediaType.APPLICATION_JSON)
                    .exchange((request, response) -> {
                        int status = response.getStatusCode().value();
                        if (status != 200) throw new Failure("HTTP_" + status);
                        var type = response.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE);
                        if (type == null || !type.toLowerCase(Locale.ROOT).contains("json")) throw new Failure("NON_JSON_RESPONSE");
                        var body = response.getBody().readNBytes(MAX_BYTES + 1);
                        if (body.length > MAX_BYTES) throw new Failure("REQUEST_OR_RESPONSE_FAILED");
                        return new String(body, StandardCharsets.UTF_8);
                    });
            // JSON 유니코드 이스케이프로 돌아온 인증키도 저장 전에 검사한다.
            if (raw.contains(apiKey) || raw.contains(encodedKey)) throw new Failure("SECRET_IN_RESPONSE");
            var decoded = mapper.readTree(raw).toString();
            if (decoded.contains(apiKey) || decoded.contains(encodedKey)) throw new Failure("SECRET_IN_RESPONSE");
            return new Response(clock.instant(), raw);
        } catch (Failure failure) { throw failure; }
        catch (Exception exception) {
            // Spring은 인터럽트 표시를 다시 세운 뒤 I/O 예외로 감싼다.
            // 외부 예외 원인에는 인증키가 든 URI나 응답 일부가 들어갈 수 있어 원인을 붙이지 않는다.
            throw new Failure(Thread.currentThread().isInterrupted() ? "REQUEST_INTERRUPTED" : "REQUEST_OR_RESPONSE_FAILED");
        }
    }

    public record Response(Instant receivedAt, String rawBody) {}
    public static final class Failure extends RuntimeException {
        public Failure(String code) { super(code); }
    }
}
