package kr.youthpolicymate.admin;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import kr.youthpolicymate.policy.catalog.PolicyApiError;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataAccessException;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestController
@Profile("!preview")
@SecurityRequirement(name = "memberSession")
public class EmailDeliveryController {
    private final EmailDeliveryStore store;
    public EmailDeliveryController(EmailDeliveryStore store) { this.store = store; }

    @GetMapping("/api/v1/admin/email-deliveries")
    @Operation(operationId = "listAdminEmailDeliveries", summary = "관리자 이메일 발송 현황",
            description = "최근 기간의 전체 요약과 상태·종류별 목록을 같은 DB 스냅샷에서 조회한다. 주소·회원 식별자·본문은 반환하지 않으며 발송 상태를 변경하지 않는다.")
    @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = EmailDeliveries.Page.class)))
    @ApiResponse(responseCode = "400", description = "조회 조건 오류", content = @Content(schema = @Schema(implementation = PolicyApiError.class)))
    @ApiResponse(responseCode = "401", description = "로그인 필요", content = @Content(schema = @Schema(implementation = PolicyApiError.class)))
    @ApiResponse(responseCode = "403", description = "관리자 권한 없음", content = @Content(schema = @Schema(implementation = PolicyApiError.class)))
    @ApiResponse(responseCode = "503", description = "발송 현황 조회 실패", content = @Content(schema = @Schema(implementation = PolicyApiError.class)))
    public ResponseEntity<EmailDeliveries.Page> list(
            @RequestParam(defaultValue = "1") @Min(1) @Max(1000) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int pageSize,
            @RequestParam(defaultValue = "7") @Min(1) @Max(90) int days,
            @RequestParam(required = false) EmailDeliveries.State state,
            @RequestParam(required = false) EmailDeliveries.Kind kind) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(store.list(page, pageSize, days, state, kind));
    }

    @ExceptionHandler({HandlerMethodValidationException.class, MethodArgumentTypeMismatchException.class})
    ResponseEntity<PolicyApiError> invalid() {
        return ResponseEntity.badRequest().cacheControl(CacheControl.noStore())
                .body(new PolicyApiError("INVALID_EMAIL_DELIVERY_QUERY", "기간·상태·종류·페이지를 확인해주세요."));
    }
    @ExceptionHandler(DataAccessException.class)
    ResponseEntity<PolicyApiError> unavailable() {
        return ResponseEntity.status(503).cacheControl(CacheControl.noStore())
                .body(new PolicyApiError("EMAIL_DELIVERY_UNAVAILABLE", "이메일 발송 내역을 불러오지 못했습니다."));
    }
}
