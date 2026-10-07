package kr.youthpolicymate.member;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Import;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.assertj.core.api.Assertions.*;

class EmailCryptoTest {
    private static final String KEY = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=";
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(Settings.class)
            .withPropertyValues("app.email.encryption-key=" + KEY, "app.email.from=sender@example.test",
                    "app.email.resend.api-key=test-api-key", "app.email.resend.webhook-secret=test-webhook-secret");

    @EnableConfigurationProperties(EmailProperties.class)
    @Import(EmailCrypto.class)
    static class Settings {}

    @Test
    @DisplayName("이메일 암호문은 매번 다르고 다른 회원·개정의 문맥으로 복호화할 수 없다")
    void bindsEncryptedValues() {
        var crypto = new EmailCrypto(KEY);
        var encrypted = crypto.encrypt("member:version:address", "first@example.test");
        assertThat(encrypted).isNotEqualTo(crypto.encrypt("member:version:address", "first@example.test"));
        assertThat(crypto.decrypt("member:version:address", encrypted)).isEqualTo("first@example.test");
        assertThatThrownBy(() -> crypto.decrypt("other:version:address", encrypted)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("발송을 끄면 설정 없이 시작하고, 켤 때 필수 설정이 빠지거나 발신 주소가 잘못되면 키를 드러내지 않고 시작을 거절한다")
    void requiresSettingsOnlyWhenEnabled() {
        new ApplicationContextRunner().withUserConfiguration(Settings.class)
                .run(context -> assertThat(context.getBean(EmailCrypto.class).ready()).isFalse());
        contextRunner.withPropertyValues("app.email.enabled=true")
                .run(context -> assertThat(context.getBean(EmailCrypto.class).ready()).isTrue());
        contextRunner.withPropertyValues("app.email.enabled=true", "app.email.resend.api-key=").run(context -> {
            assertThat(context).hasFailed();
            var trace = new StringWriter();
            context.getStartupFailure().printStackTrace(new PrintWriter(trace));
            assertThat(trace.toString()).contains("app.email").doesNotContain(KEY, "test-webhook-secret");
        });
        contextRunner.withPropertyValues("app.email.from=first..last@example.test").run(context -> assertThat(context).hasFailed());
    }
}
