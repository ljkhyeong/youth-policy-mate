package kr.youthpolicymate.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;

/** 브라우저가 접근하는 웹·API 주소. 끝의 `/`를 지워 경로를 이어 붙일 때 `//`가 생기지 않게 한다. */
@Validated
@ConfigurationProperties("app.url")
public record AppUrls(@NotBlank String frontend, @NotBlank String backend) {
    public AppUrls {
        frontend = StringUtils.trimTrailingCharacter(frontend, '/');
        backend = StringUtils.trimTrailingCharacter(backend, '/');
    }
}
