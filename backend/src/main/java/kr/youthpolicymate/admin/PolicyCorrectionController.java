package kr.youthpolicymate.admin;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import kr.youthpolicymate.policy.catalog.PolicyApiError;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.security.Principal;
import java.util.UUID;

@RestController
@Profile("!preview")
@RequestMapping("/api/v1/admin/policy-corrections")
@SecurityRequirement(name = "memberSession")
@ApiResponse(responseCode = "400", description = "입력 오류", content = @Content(schema = @Schema(implementation = PolicyApiError.class)))
@ApiResponse(responseCode = "401", description = "로그인 필요", content = @Content(schema = @Schema(implementation = PolicyApiError.class)))
@ApiResponse(responseCode = "403", description = "관리자 권한 없음", content = @Content(schema = @Schema(implementation = PolicyApiError.class)))
@ApiResponse(responseCode = "503", description = "보정 처리 또는 조회 실패", content = @Content(schema = @Schema(implementation = PolicyApiError.class)))
public class PolicyCorrectionController {
    private final PolicyCorrectionService service;
    private final CollectionExceptionStore policies;

    PolicyCorrectionController(PolicyCorrectionService service, CollectionExceptionStore policies) {
        this.service = service;
        this.policies = policies;
    }

    @GetMapping
    @Operation(operationId = "listPolicyCorrections", summary = "관리자 정책 보정·충돌·해제 이력")
    public ResponseEntity<PolicyCorrections.Page> list(@RequestParam(defaultValue = "1") @Min(1) @Max(1000) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int pageSize) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.list(page, pageSize));
    }

    @GetMapping("/policies/{number}")
    @Operation(operationId = "getCorrectionPolicy", summary = "보정할 공개 정책의 현재 내용 조회")
    @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = CollectionExceptions.CurrentPolicy.class)))
    @ApiResponse(responseCode = "404", description = "공개 정책 없음", content = @Content(schema = @Schema(implementation = PolicyApiError.class)))
    public ResponseEntity<?> policy(@PathVariable @Pattern(regexp = "[0-9]{1,100}") String number) {
        return policies.currentPolicy(number).<ResponseEntity<?>>map(value -> ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(value))
                .orElseGet(() -> error(404, "CORRECTION_POLICY_NOT_FOUND", "공개된 정책을 찾을 수 없습니다."));
    }

    @PostMapping
    @Operation(operationId = "createPolicyCorrection", summary = "공개 정책의 정책명 또는 운영 기관 보정",
            description = "정책당 한 항목을 보정하며 원본을 유지한다. 같은 요청 ID·입력·작업자는 기존 결과를 반환한다.")
    @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = PolicyCorrections.Item.class)))
    @ApiResponse(responseCode = "409", description = "개정 변경·진행 중 보정·요청 ID 충돌", content = @Content(schema = @Schema(implementation = PolicyApiError.class)))
    public ResponseEntity<PolicyCorrections.Item> create(@Valid @RequestBody PolicyCorrections.Request request, Principal principal) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.create(request, UUID.fromString(principal.getName())));
    }

    @PostMapping("/{id}/resolutions")
    @Operation(operationId = "resolvePolicyCorrection", summary = "정책 보정 해제 또는 새 원본 기준 보정 유지",
            description = "조회한 현재 개정과 검토 원본 ID가 같을 때만 처리한다. KEEP은 충돌 상태에서만 허용한다. 처리 후 남은 수집 실패 항목은 별도로 재처리한다.")
    @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = PolicyCorrections.Item.class)))
    @ApiResponse(responseCode = "409", description = "개정·검토 원본 변경 또는 요청 ID 충돌", content = @Content(schema = @Schema(implementation = PolicyApiError.class)))
    public ResponseEntity<PolicyCorrections.Item> resolve(@PathVariable UUID id, @Valid @RequestBody PolicyCorrections.Resolution request, Principal principal) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.resolve(id, request, UUID.fromString(principal.getName())));
    }

    @ExceptionHandler({PolicyCorrections.Invalid.class, MethodArgumentNotValidException.class, HttpMessageNotReadableException.class,
            HandlerMethodValidationException.class, MethodArgumentTypeMismatchException.class})
    ResponseEntity<PolicyApiError> invalid() {
        return error(400, "INVALID_POLICY_CORRECTION", "보정 항목·값·사유와 요청 정보를 확인해주세요.");
    }

    @ExceptionHandler({PolicyCorrections.Changed.class, DuplicateKeyException.class})
    ResponseEntity<PolicyApiError> changed() {
        return error(409, "POLICY_CORRECTION_CHANGED", "정책이나 보정 상태가 바뀌었습니다. 최신 내용을 다시 확인해주세요.");
    }

    @ExceptionHandler(DataAccessException.class)
    ResponseEntity<PolicyApiError> unavailable() {
        return error(503, "POLICY_CORRECTION_UNAVAILABLE", "보정 처리 결과를 확인하지 못했습니다. 같은 요청으로 다시 확인해주세요.");
    }

    private static ResponseEntity<PolicyApiError> error(int status, String code, String message) {
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(new PolicyApiError(code, message));
    }
}
