package kr.youthpolicymate.member;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.net.URI;

import static org.springframework.util.StringUtils.hasText;

/**
 * 이메일 발송·암호화·Resend 설정. 바인딩 검증에 실패하면 거절한 값이 기동 로그에 남으므로
 * 키 필드에는 제약을 달지 않고 활성화 조건만 검사하며, 문자열 표현에도 값을 넣지 않는다.
 */
@Validated
@ConfigurationProperties("app.email")
record EmailProperties(boolean enabled, String encryptionKey, @Email @Size(max = 254) String from,
                       @DefaultValue Resend resend) {
    record Resend(String apiKey, String readApiKey, String webhookSecret,
                  @DefaultValue("https://api.resend.com") URI baseUrl) {
        @Override public String toString() { return "Resend[설정 비공개]"; }
    }

    @AssertTrue(message = "이메일을 켜려면 암호화 키·발신 주소·Resend API 키·웹훅 서명 키가 필요합니다.")
    boolean isComplete() {
        return !enabled || hasText(encryptionKey) && hasText(from) && hasText(resend.apiKey()) && hasText(resend.webhookSecret());
    }

    @Override public String toString() { return "EmailProperties[설정 비공개]"; }
}
