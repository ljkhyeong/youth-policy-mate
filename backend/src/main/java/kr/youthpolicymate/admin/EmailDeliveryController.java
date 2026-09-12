package kr.youthpolicymate.admin;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import kr.youthpolicymate.policy.catalog.PolicyApiError;
import kr.youthpolicymate.member.ResendEmailLookup;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataAccessException;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.Clock;
import java.util.UUID;

@RestController
@Profile("!preview")
@SecurityRequirement(name = "memberSession")
public class EmailDeliveryController {
    private final EmailDeliveryStore store;
    private final ResendEmailLookup lookup;
    private final Clock clock;
    public EmailDeliveryController(EmailDeliveryStore store, ResendEmailLookup lookup, Clock clock) {
        this.store = store; this.lookup = lookup; this.clock = clock;
    }

    @GetMapping(value = "/api/v1/admin/email-deliveries/{id}/provider-status", produces = "application/json")
    @Operation(operationId = "getAdminEmailProviderStatus", summary = "Resend 이메일 최신 상태 조회",
            description = "기록된 공급자 발송 ID로 조회한다. 주소·본문은 반환하지 않고 DB 상태·수신 동의·발송 요청을 변경하지 않는다. checkedAt은 조회 시각이며 이벤트 발생 시각이 아니다.")
    @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = EmailDeliveries.ProviderStatus.class)))
    @ApiResponse(responseCode = "400", description = "잘못된 요청 ID", content = @Content(schema = @Schema(implementation = PolicyApiError.class)))
    @ApiResponse(responseCode = "401", description = "로그인 필요", content = @Content(schema = @Schema(implementation = PolicyApiError.class)))
    @ApiResponse(responseCode = "403", description = "관리자 권한 없음", content = @Content(schema = @Schema(implementation = PolicyApiError.class)))
    @ApiResponse(responseCode = "404", description = "조회 가능한 Resend 발송 기록 없음", content = @Content(schema = @Schema(implementation = PolicyApiError.class)))
    @ApiResponse(responseCode = "503", description = "공급자 조회 불가", content = @Content(schema = @Schema(implementation = PolicyApiError.class)))
    public ResponseEntity<?> providerStatus(@PathVariable UUID id) {
        UUID messageId = store.providerMessageId(id);
        if (messageId == null) return ResponseEntity.status(404).cacheControl(CacheControl.noStore())
                .body(new PolicyApiError("EMAIL_PROVIDER_ID_MISSING", "조회할 Resend 발송 ID가 없습니다."));
        var event = lookup.retrieve(messageId);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new EmailDeliveries.ProviderStatus(event, clock.instant()));
    }

    @ExceptionHandler(ResendEmailLookup.Unavailable.class)
    ResponseEntity<PolicyApiError> providerUnavailable(ResendEmailLookup.Unavailable failure) {
        String message = switch (failure.reason()) {
            case NOT_CONFIGURED -> "Resend 조회용 API 키가 설정되지 않았습니다.";
            case ACCESS_DENIED -> "Resend 조회용 API 키의 권한을 확인해주세요.";
            case NOT_FOUND -> "Resend에서 발송 기록을 찾지 못했습니다. 전달 실패를 뜻하지 않습니다.";
            case RATE_LIMITED -> "Resend 조회 한도에 도달했습니다. 잠시 후 다시 조회해주세요.";
            case UNAVAILABLE -> "Resend 상태를 불러오지 못했습니다. 잠시 후 다시 조회해주세요.";
        };
        return ResponseEntity.status(503).cacheControl(CacheControl.noStore())
                .body(new PolicyApiError("EMAIL_PROVIDER_" + failure.reason().name(), message));
    }

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
                .body(new PolicyApiError("INVALID_EMAIL_DELIVERY_QUERY", "조회 조건과 요청 ID를 확인해주세요."));
    }
    @ExceptionHandler(DataAccessException.class)
    ResponseEntity<PolicyApiError> unavailable() {
        return ResponseEntity.status(503).cacheControl(CacheControl.noStore())
                .body(new PolicyApiError("EMAIL_DELIVERY_UNAVAILABLE", "이메일 발송 내역을 불러오지 못했습니다."));
    }
}
