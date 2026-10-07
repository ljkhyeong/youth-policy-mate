package kr.youthpolicymate.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;

/**
 * 브라우저가 접근하는 웹·API 주소. 끝의 `/`를 지워 경로를 이어 붙일 때 `//`가 생기지 않게 한다.
 * 설정 바인딩은 해석하지 못한 자리표시자를 문자열 그대로 넘기므로 절대 주소 형식까지 기동 시 검사한다.
 */
@Validated
@ConfigurationProperties("app.url")
public record AppUrls(@NotBlank @Pattern(regexp = ABSOLUTE) String frontend, @NotBlank @Pattern(regexp = ABSOLUTE) String backend) {
    static final String ABSOLUTE = "https?://[^\\s{}$]+";

    public AppUrls {
        frontend = StringUtils.trimTrailingCharacter(frontend, '/');
        backend = StringUtils.trimTrailingCharacter(backend, '/');
    }
}
