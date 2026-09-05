package kr.youthpolicymate.member;

import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.util.List;

@Configuration(proxyBeanMethods = false)
@Profile("!preview")
class MemberApiConfiguration {
    @Bean
    OpenApiCustomizer memberContract() {
        return api -> {
            var conditions = api.getComponents().getSchemas().get("MemberConditions");
            if (conditions == null) return;
            // 참조 대상의 object 제약과 null 타입을 같은 스키마에 놓으면 null 응답이 거절된다.
            conditions.addProperty("conditions", new Schema<>().anyOf(List.of(
                    new Schema<>().$ref("#/components/schemas/BasicConditions"), new Schema<>().types(java.util.Set.of("null")))));
            api.getComponents().addSecuritySchemes("memberSession", new SecurityScheme().type(SecurityScheme.Type.APIKEY)
                    .in(SecurityScheme.In.COOKIE).name("YPM_SESSION").description("카카오·네이버 로그인 후 발급하는 서버 세션"));
            api.path("/api/v1/logout", new PathItem().post(new Operation().operationId("logoutMember").summary("현재 세션 로그아웃")
                    .responses(new io.swagger.v3.oas.models.responses.ApiResponses().addApiResponse("204", new ApiResponse().description("로그아웃 완료")))));
            api.getPaths().forEach((path, item) -> {
                if (!path.startsWith("/api/v1/me/") && !path.equals("/api/v1/logout")) return;
                item.readOperationsMap().forEach((method, operation) -> {
                    operation.addSecurityItem(new SecurityRequirement().addList("memberSession"));
                    operation.getResponses().addApiResponse("401", new ApiResponse().description("로그인 필요"));
                    operation.getResponses().addApiResponse("403", new ApiResponse().description("권한 또는 CSRF 토큰 확인 필요"));
                    if (method != PathItem.HttpMethod.GET) operation.addParametersItem(new Parameter().in("header").name("X-CSRF-TOKEN")
                            .required(true).description("GET /api/v1/session에서 받은 csrfToken").schema(new Schema<>().types(java.util.Set.of("string"))));
                });
            });
        };
    }
}
