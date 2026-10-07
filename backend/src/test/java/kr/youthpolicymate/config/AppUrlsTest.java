package kr.youthpolicymate.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class AppUrlsTest {
    @EnableConfigurationProperties(AppUrls.class)
    static class Bind {}

    private final ApplicationContextRunner context = new ApplicationContextRunner().withUserConfiguration(Bind.class);

    @Test
    @DisplayName("운영 공개 주소가 없거나 해석되지 않으면 기동을 막는다")
    void rejectsMissingPublicAddress() {
        context.withPropertyValues("app.url.frontend=${PUBLIC_APP_URL:}", "app.url.backend=${PUBLIC_APP_URL:}")
                .run(started -> assertThat(started).hasFailed());
        context.withPropertyValues("app.url.frontend=${PUBLIC_APP_URL}", "app.url.backend=${PUBLIC_APP_URL}")
                .run(started -> assertThat(started).hasFailed());
        context.withPropertyValues("app.url.frontend=example.test", "app.url.backend=https://example.test")
                .run(started -> assertThat(started).hasFailed());
    }

    @Test
    @DisplayName("끝의 슬래시를 지운 절대 주소로 바인딩한다")
    void trimsTrailingSlash() {
        context.withPropertyValues("app.url.frontend=https://example.test/", "app.url.backend=http://127.0.0.1:8080")
                .run(started -> {
                    var urls = started.getBean(AppUrls.class);
                    assertThat(urls.frontend()).isEqualTo("https://example.test");
                    assertThat(urls.backend()).isEqualTo("http://127.0.0.1:8080");
                });
    }
}
