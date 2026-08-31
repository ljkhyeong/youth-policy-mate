package kr.youthpolicymate.devpreview;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.servers.Server;
import io.swagger.v3.oas.models.media.Schema;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springdoc.core.customizers.OpenApiCustomizer;

@Configuration(proxyBeanMethods = false)
@Profile("preview")
@OpenAPIDefinition(
        info = @Info(title = "청년정책메이트 개발 전용 인공 자료 API", version = "preview-1",
                description = "고정 인공 자료의 계산 결과 조회만 제공한다. 실제 정책·회원·예약·발송 API가 아니다."),
        servers = @Server(url = "/"))
class PreviewApiConfiguration {

    @Bean
    OpenApiCustomizer eligibilityUncertaintySchema() {
        // OpenAPI 3.1에서는 타입뿐 아니라 enum 값 목록에도 실제 null이 있어야 null 응답을 허용한다.
        return openApi -> {
            Schema<?> condition = openApi.getComponents().getSchemas().get("EligibilityCondition");
            Schema<?> uncertainty = condition.getProperties().get("uncertainty");
            uncertainty.addEnumItemObject(null);
        };
    }

    @Bean
    @Order(0)
    SecurityFilterChain previewApiSecurity(HttpSecurity http) throws Exception {
        return http.securityMatcher("/api/dev/reminder-examples", "/api/dev/eligibility-examples", "/dev/openapi")
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers(HttpMethod.GET, "/api/dev/reminder-examples", "/api/dev/eligibility-examples", "/dev/openapi").permitAll()
                        .anyRequest().denyAll())
                .build();
    }
}
