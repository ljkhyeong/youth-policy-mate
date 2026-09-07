package kr.youthpolicymate.member;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.jdbc.JdbcIndexedSessionRepository;
import org.springframework.test.annotation.DirtiesContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "app.admin.member-ids=10000000-0000-0000-0000-000000000001", "APP_COOKIE_SECURE=false",
        "KAKAO_CLIENT_ID=test-kakao", "KAKAO_CLIENT_SECRET=test-secret",
        "NAVER_CLIENT_ID=test-naver", "NAVER_CLIENT_SECRET=test-secret",
        "app.reminders.enabled=false", "app.email.enabled=false", "app.ontong.schedule.enabled=false"})
@Import(OAuthLoginFlowTest.ProviderConfiguration.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class OAuthLoginFlowTest {
    private static final String ADMIN = "10000000-0000-0000-0000-000000000001";
    @Container @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18.6-alpine");
    @LocalServerPort int port;
    @Autowired JdbcClient jdbc;
    @Autowired ObjectMapper mapper;
    @Autowired TestProvider provider;
    @Autowired OAuth2AuthorizedClientService authorizedClients;
    @Autowired JdbcIndexedSessionRepository sessions;
    private final List<HttpClient> clients = new ArrayList<>();

    @BeforeEach void reset() {
        jdbc.sql("TRUNCATE members, spring_session CASCADE").update();
        jdbc.sql("INSERT INTO members(id, provider, provider_subject, display_name) VALUES (:id, 'kakao', '777', '검증 관리자')")
                .param("id", UUID.fromString(ADMIN)).update();
        provider.mode = "normal";
        provider.tokenCalls.set(0);
        provider.userCalls.set(0);
        provider.tokenForm = Map.of();
    }

    @AfterEach void closeClients() { clients.forEach(HttpClient::close); }

    @ParameterizedTest @ValueSource(strings = {"kakao", "naver"})
    @DisplayName("실제 OAuth 콜백은 세션을 교체하고 제공자 회원·관리자 권한·CSRF·로그아웃을 연결한다")
    void createsAndInvalidatesSession(String registration) throws Exception {
        var browser = browser();
        var before = session(browser);
        var anonymousCookie = cookie(browser);
        var start = get(browser, "/oauth2/authorization/" + registration);
        var authorization = location(start);
        assertThat(authorization).startsWith(provider.base() + "/" + registration + "/authorize?");
        var callback = location(get(browser, authorization));
        assertThat(location(get(browser, callback))).isEqualTo("http://127.0.0.1:3000/login/complete");
        var authenticatedCookie = cookie(browser);
        assertThat(authenticatedCookie).isNotEqualTo(anonymousCookie);
        var loggedIn = session(browser);
        assertThat(loggedIn.path("authenticated").asBoolean()).isTrue();
        assertThat(loggedIn.path("displayName").asString()).isEqualTo("검증 회원");
        assertThat(loggedIn.toString()).doesNotContain("private@example.test", "fixture-access-token", "test-secret");
        assertThat(provider.tokenForm).containsEntry("grant_type", "authorization_code")
                .containsEntry("client_id", "test-" + registration).containsEntry("client_secret", "test-secret")
                .containsEntry("redirect_uri", base() + "/login/oauth2/code/" + registration);

        var memberId = jdbc.sql("SELECT id FROM members WHERE provider = :provider AND provider_subject = '777'")
                .param("provider", registration).query(UUID.class).single();
        Map<String, ? extends org.springframework.session.Session> saved = sessions.findByIndexNameAndIndexValue(FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME, memberId.toString());
        assertThat(saved).hasSize(1);
        SecurityContext context = saved.values().iterator().next().getAttribute("SPRING_SECURITY_CONTEXT");
        var principal = (OAuth2User) context.getAuthentication().getPrincipal();
        assertThat(principal.getAttributes()).containsOnlyKeys("memberId", "displayName", "suggestedBirthDate");
        org.springframework.security.oauth2.client.OAuth2AuthorizedClient client = authorizedClients.loadAuthorizedClient(registration, memberId.toString());
        assertThat(client).isNull();
        assertThat(get(browser, "/api/v1/admin/policy-corrections").statusCode()).isEqualTo(registration.equals("kakao") ? 200 : 403);
        assertThat(getWithCookie(anonymousCookie, "/api/v1/me/conditions").statusCode()).isEqualTo(401);
        assertThat(jdbc.sql("SELECT count(*) FROM members").query(Long.class).single()).isEqualTo(registration.equals("kakao") ? 1 : 2);

        assertThat(logout(browser, before.path("csrfToken").asString()).statusCode()).isEqualTo(403);
        assertThat(session(browser).path("authenticated").asBoolean()).isTrue();
        assertThat(logout(browser, loggedIn.path("csrfToken").asString()).statusCode()).isEqualTo(204);
        assertThat(getWithCookie(authenticatedCookie, "/api/v1/admin/policy-corrections").statusCode()).isEqualTo(401);
        assertThat(sessions.findByIndexNameAndIndexValue(FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME, memberId.toString())).isEmpty();
        assertThat(session(browser).path("authenticated").asBoolean()).isFalse();
        assertThat(provider.tokenCalls).hasValue(1);
        assertThat(provider.userCalls).hasValue(1);
    }

    @ParameterizedTest @ValueSource(strings = {"bad-state", "denied", "token-failed", "invalid-profile"})
    @DisplayName("state 불일치·동의 거절·토큰 오류·잘못된 프로필은 회원 세션을 만들지 않는다")
    void rejectsIncompleteLogin(String mode) throws Exception {
        provider.mode = mode;
        var browser = browser();
        var authorization = location(get(browser, "/oauth2/authorization/naver"));
        var callback = location(get(browser, authorization));
        assertThat(location(get(browser, callback))).isEqualTo("http://127.0.0.1:3000/login?error=login");
        assertThat(session(browser).path("authenticated").asBoolean()).isFalse();
        assertThat(get(browser, "/api/v1/admin/policy-corrections").statusCode()).isEqualTo(401);
        assertThat(jdbc.sql("SELECT count(*) FROM members").query(Long.class).single()).isOne();
        int expectedTokens = List.of("bad-state", "denied").contains(mode) ? 0 : 1;
        assertThat(provider.tokenCalls).hasValue(expectedTokens);
        assertThat(provider.userCalls).hasValue(mode.equals("invalid-profile") ? 1 : 0);
        // 사용한 인가 요청은 실패 후에도 다시 사용할 수 없다.
        assertThat(location(get(browser, callback))).isEqualTo("http://127.0.0.1:3000/login?error=login");
        assertThat(provider.tokenCalls).hasValue(expectedTokens);
    }

    private HttpClient browser() {
        var client = HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ORIGINAL_SERVER))
                .connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build();
        clients.add(client);
        return client;
    }
    private String base() { return "http://127.0.0.1:" + port; }
    private JsonNode session(HttpClient browser) throws Exception {
        var response = get(browser, "/api/v1/session");
        assertThat(response.statusCode()).isEqualTo(200);
        return mapper.readTree(response.body());
    }
    private HttpResponse<String> get(HttpClient browser, String path) throws Exception {
        return browser.send(HttpRequest.newBuilder(URI.create(path.startsWith("/") ? base() + path : path))
                .timeout(Duration.ofSeconds(5)).GET().build(), HttpResponse.BodyHandlers.ofString());
    }
    private String location(HttpResponse<String> response) {
        assertThat(response.statusCode()).isEqualTo(302);
        return response.headers().firstValue("Location").orElseThrow();
    }
    private String cookie(HttpClient browser) {
        return ((CookieManager) browser.cookieHandler().orElseThrow()).getCookieStore().getCookies().stream()
                .filter(value -> value.getName().equals("YPM_SESSION") && !value.hasExpired()).findFirst().orElseThrow().getValue();
    }
    private HttpResponse<String> getWithCookie(String cookie, String path) throws Exception {
        return browser().send(HttpRequest.newBuilder(URI.create(base() + path)).header("Cookie", "YPM_SESSION=" + cookie)
                .timeout(Duration.ofSeconds(5)).GET().build(), HttpResponse.BodyHandlers.ofString());
    }
    private HttpResponse<String> logout(HttpClient browser, String csrf) throws Exception {
        return browser.send(HttpRequest.newBuilder(URI.create(base() + "/api/v1/logout")).header("X-CSRF-TOKEN", csrf)
                .timeout(Duration.ofSeconds(5)).POST(HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString());
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ProviderConfiguration {
        @Bean TestProvider testProvider() throws IOException { return new TestProvider(); }

        @Bean @Primary
        InMemoryClientRegistrationRepository testRegistrations(@Qualifier("clientRegistrationRepository") InMemoryClientRegistrationRepository configured,
                TestProvider provider) {
            var registrations = new ArrayList<ClientRegistration>();
            configured.forEach(value -> registrations.add(ClientRegistration.withClientRegistration(value)
                    .authorizationUri(provider.base() + "/" + value.getRegistrationId() + "/authorize")
                    .tokenUri(provider.base() + "/" + value.getRegistrationId() + "/token")
                    .userInfoUri(provider.base() + "/" + value.getRegistrationId() + "/user")
                    .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}").build()));
            return new InMemoryClientRegistrationRepository(registrations);
        }
    }

    static class TestProvider implements AutoCloseable {
        private final HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        private final AtomicInteger tokenCalls = new AtomicInteger();
        private final AtomicInteger userCalls = new AtomicInteger();
        private volatile String mode = "normal";
        private volatile Map<String, String> tokenForm = Map.of();

        TestProvider() throws IOException {
            server.createContext("/", exchange -> {
                var path = exchange.getRequestURI().getPath();
                if (path.endsWith("/authorize")) {
                    var query = parameters(exchange.getRequestURI().getRawQuery());
                    var result = mode.equals("denied") ? "error=access_denied" : "code=fixture-code";
                    var state = mode.equals("bad-state") ? "different-state" : query.get("state");
                    exchange.getResponseHeaders().set("Location", query.get("redirect_uri") + "?" + result + "&state="
                            + java.net.URLEncoder.encode(state, StandardCharsets.UTF_8));
                    exchange.sendResponseHeaders(302, -1);
                    exchange.close();
                    return;
                }
                String body;
                int status = 200;
                if (path.endsWith("/token")) {
                    tokenCalls.incrementAndGet();
                    tokenForm = parameters(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                    if (mode.equals("token-failed")) { status = 400; body = "{\"error\":\"invalid_grant\"}"; }
                    else body = "{\"access_token\":\"fixture-access-token\",\"token_type\":\"Bearer\",\"expires_in\":3600}";
                } else {
                    userCalls.incrementAndGet();
                    body = mode.equals("invalid-profile") ? "{\"response\":{\"nickname\":\"식별자 없는 회원\"}}"
                            : path.startsWith("/kakao/")
                            ? "{\"id\":777,\"kakao_account\":{\"email\":\"private@example.test\",\"profile\":{\"nickname\":\"검증 회원\"}}}"
                            : "{\"response\":{\"id\":\"777\",\"email\":\"private@example.test\",\"nickname\":\"검증 회원\"}}";
                }
                exchange.getResponseHeaders().set("Content-Type", "application/json;charset=UTF-8");
                var bytes = body.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(status, bytes.length);
                try (var output = exchange.getResponseBody()) { output.write(bytes); }
            });
            server.start();
        }
        String base() { return "http://localhost:" + server.getAddress().getPort(); }
        @Override public void close() { server.stop(0); }
        private static Map<String, String> parameters(String encoded) {
            var values = new LinkedHashMap<String, String>();
            for (var entry : encoded.split("&")) {
                var pair = entry.split("=", 2);
                values.put(URLDecoder.decode(pair[0], StandardCharsets.UTF_8), URLDecoder.decode(pair[1], StandardCharsets.UTF_8));
            }
            return values;
        }
    }
}
