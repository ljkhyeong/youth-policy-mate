package kr.youthpolicymate.member;

import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Set;

@Configuration(proxyBeanMethods = false)
class MemberApiConfiguration {
    /** 회원 세션을 쓰는 회원·관리자 API와 로그아웃에 세션·CSRF 요구와 401·403 응답을 한 규칙으로 적는다. */
    @Bean
    OpenApiCustomizer sessionApiContract() {
        return api -> {
            var error = new Content().addMediaType("application/json",
                    new MediaType().schema(new Schema<>().$ref("#/components/schemas/PolicyApiError")));
            api.getComponents().addSecuritySchemes("memberSession", new SecurityScheme().type(SecurityScheme.Type.APIKEY)
                    .in(SecurityScheme.In.COOKIE).name("YPM_SESSION").description("카카오·네이버 로그인 후 발급하는 서버 세션"));
            api.path("/api/v1/logout", new PathItem().post(new Operation().operationId("logoutMember").summary("현재 세션 로그아웃")
                    .responses(new ApiResponses().addApiResponse("204", new ApiResponse().description("로그아웃 완료")))));
            api.getPaths().forEach((path, item) -> {
                if (!path.startsWith("/api/v1/me/") && !path.startsWith("/api/v1/admin/") && !path.equals("/api/v1/logout")) return;
                item.readOperationsMap().forEach((method, operation) -> {
                    operation.addSecurityItem(new SecurityRequirement().addList("memberSession"));
                    if (path.startsWith("/api/v1/me/email-")) {
                        for (String status : List.of("400", "409", "429", "503")) operation.getResponses().addApiResponse(status,
                                new ApiResponse().description(switch (status) {
                                    case "400" -> "입력 또는 확인 코드 오류";
                                    case "409" -> "이메일 확인 필요";
                                    case "429" -> "확인 메일 요청 횟수 초과";
                                    default -> "이메일 설정 또는 저장소 사용 불가";
                                }).content(error));
                    }
                    operation.getResponses().addApiResponse("401", new ApiResponse().description("로그인 필요").content(error));
                    operation.getResponses().addApiResponse("403", new ApiResponse().description("권한 또는 CSRF 토큰 확인 필요").content(error));
                    if (method != PathItem.HttpMethod.GET) operation.addParametersItem(new Parameter().in("header").name("X-CSRF-TOKEN")
                            .required(true).description("GET /api/v1/session에서 받은 csrfToken").schema(new Schema<>().types(Set.of("string"))));
                });
            });
        };
    }
}
