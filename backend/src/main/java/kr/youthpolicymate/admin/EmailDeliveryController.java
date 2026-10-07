package kr.youthpolicymate.admin;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import kr.youthpolicymate.config.ApiException;
import kr.youthpolicymate.policy.catalog.PolicyApiError;
import kr.youthpolicymate.member.ResendEmailLookup;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.util.UUID;

@RestController
@Profile("!preview")
@ApiResponse(responseCode = "200")
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
    @ApiResponse(responseCode = "400", description = "잘못된 요청 ID", content = @Content(schema = @Schema(implementation = PolicyApiError.class)))
    @ApiResponse(responseCode = "404", description = "조회 가능한 Resend 발송 기록 없음", content = @Content(schema = @Schema(implementation = PolicyApiError.class)))
    @ApiResponse(responseCode = "503", description = "공급자 조회 불가", content = @Content(schema = @Schema(implementation = PolicyApiError.class)))
    public EmailDeliveries.ProviderStatus providerStatus(@PathVariable UUID id) {
        UUID messageId = store.providerMessageId(id).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "EMAIL_PROVIDER_ID_MISSING", "조회할 Resend 발송 ID가 없습니다."));
        return new EmailDeliveries.ProviderStatus(lookup.retrieve(messageId), clock.instant());
    }

    @GetMapping("/api/v1/admin/email-deliveries")
    @Operation(operationId = "listAdminEmailDeliveries", summary = "관리자 이메일 발송 현황",
            description = "최근 기간의 전체 요약과 상태·종류별 목록을 같은 DB 스냅샷에서 조회한다. 주소·회원 식별자·본문은 반환하지 않으며 발송 상태를 변경하지 않는다.")
    @ApiResponse(responseCode = "400", description = "조회 조건 오류", content = @Content(schema = @Schema(implementation = PolicyApiError.class)))
    @ApiResponse(responseCode = "503", description = "발송 현황 조회 실패", content = @Content(schema = @Schema(implementation = PolicyApiError.class)))
    public EmailDeliveries.Page list(
            @RequestParam(defaultValue = "1") @Min(1) @Max(1000) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int pageSize,
            @RequestParam(defaultValue = "7") @Min(1) @Max(90) int days,
            @RequestParam(required = false) EmailDeliveries.State state,
            @RequestParam(required = false) EmailDeliveries.Kind kind) {
        return store.list(page, pageSize, days, state, kind);
    }
}
