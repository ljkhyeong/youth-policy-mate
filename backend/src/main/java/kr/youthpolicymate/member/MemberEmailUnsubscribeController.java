package kr.youthpolicymate.member;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@Profile("!preview")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@RequestMapping("/api/v1/email-unsubscribe/{token}")
public class MemberEmailUnsubscribeController {
    private final MemberEmailUnsubscribeStore store;
    private final String frontend;

    public MemberEmailUnsubscribeController(MemberEmailUnsubscribeStore store, Environment environment) {
        this.store = store;
        frontend = environment.getProperty("APP_FRONTEND_URL", "http://127.0.0.1:3000").replaceAll("/+$", "");
    }

    @GetMapping
    @Operation(operationId = "openEmailUnsubscribe", summary = "수신 해제 확인 화면 이동 · 설정 변경 없음")
    @ApiResponse(responseCode = "303", description = "확인 화면 이동")
    public ResponseEntity<Void> open(@PathVariable @Pattern(regexp = "[A-Za-z0-9_-]{43}") String token) {
        return ResponseEntity.status(303).cacheControl(CacheControl.noStore()).header("Referrer-Policy", "no-referrer")
                .location(URI.create(frontend + "/email-unsubscribe#" + token)).build();
    }

    @PostMapping(consumes = {MediaType.APPLICATION_FORM_URLENCODED_VALUE, MediaType.MULTIPART_FORM_DATA_VALUE})
    @Operation(operationId = "unsubscribeEmail", summary = "메일 링크로 이메일 알림 수신 해제",
            description = "쿠키·로그인·CSRF 토큰 없이 수신 해제 전용 토큰으로 처리한다. 이전 주소 링크는 새 설정을 변경하지 않는다.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true, content = {
                    @Content(mediaType = MediaType.APPLICATION_FORM_URLENCODED_VALUE, schema = @Schema(implementation = Form.class)),
                    @Content(mediaType = MediaType.MULTIPART_FORM_DATA_VALUE, schema = @Schema(implementation = Form.class))}))
    @ApiResponse(responseCode = "200", description = "수신 해제 처리 완료 · 이미 해제된 주소도 같은 응답")
    @ApiResponse(responseCode = "400", description = "토큰 형식 또는 One-Click 요청 오류")
    @ApiResponse(responseCode = "404", description = "사용할 수 없는 링크")
    @ApiResponse(responseCode = "503", description = "저장소 사용 불가")
    public ResponseEntity<Void> unsubscribe(@PathVariable @Pattern(regexp = "[A-Za-z0-9_-]{43}") String token,
            @io.swagger.v3.oas.annotations.Parameter(hidden = true) @Valid @ModelAttribute Form input) {
        return ResponseEntity.status(store.unsubscribe(token) ? 200 : 404).cacheControl(CacheControl.noStore()).build();
    }

    @Schema(name = "EmailUnsubscribeForm", requiredProperties = "List-Unsubscribe")
    public record Form(@BindParam("List-Unsubscribe") @com.fasterxml.jackson.annotation.JsonProperty("List-Unsubscribe")
                       @NotNull @Pattern(regexp = "One-Click") @Schema(allowableValues = "One-Click") String action) {}
}
