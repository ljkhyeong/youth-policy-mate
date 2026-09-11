package kr.youthpolicymate.admin;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
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
@RequestMapping("/api/v1/admin/policy-rule-reviews/{number}")
@SecurityRequirement(name = "memberSession")
@ApiResponse(responseCode = "400", description = "규칙·사유 입력 오류", content = @Content(schema = @Schema(implementation = PolicyApiError.class)))
@ApiResponse(responseCode = "401", description = "로그인 필요", content = @Content(schema = @Schema(implementation = PolicyApiError.class)))
@ApiResponse(responseCode = "403", description = "관리자 권한 없음", content = @Content(schema = @Schema(implementation = PolicyApiError.class)))
@ApiResponse(responseCode = "404", description = "정책·규칙 없음", content = @Content(schema = @Schema(implementation = PolicyApiError.class)))
@ApiResponse(responseCode = "409", description = "원문·기간·적용 버전·요청 정보 변경", content = @Content(schema = @Schema(implementation = PolicyApiError.class)))
@ApiResponse(responseCode = "503", description = "처리 결과 미확인", content = @Content(schema = @Schema(implementation = PolicyApiError.class)))
class PolicyRuleManagementController {
    private final PolicyRuleManagementService service;
    PolicyRuleManagementController(PolicyRuleManagementService service) { this.service = service; }

    @PostMapping("/drafts")
    @Operation(operationId = "createPolicyRuleDraft", summary = "검토한 규칙 파일을 새 초안으로 등록",
            description = "같은 요청 ID·입력·작업자는 기존 처리 결과를 반환한다. 초안 등록만으로 질문을 공개하지 않는다.")
    public ResponseEntity<PolicyRuleActions.Result> draft(@PathVariable @Pattern(regexp = "[0-9]{20}") String number,
            @Valid @RequestBody PolicyRuleActions.Draft request, Principal principal) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.draft(number, request, UUID.fromString(principal.getName())));
    }

    @PostMapping("/versions/{id}/publish")
    @Operation(operationId = "publishPolicyRuleDraft", summary = "검토한 초안을 현재 질문·판정에 적용",
            description = "조회한 개정·현재 적용 버전·원문·기간을 확인한다. 같은 요청 재시도는 기존 결과만 반환한다.")
    public ResponseEntity<PolicyRuleActions.Result> publish(@PathVariable @Pattern(regexp = "[0-9]{20}") String number,
            @PathVariable UUID id, @Valid @RequestBody PolicyRuleActions.Publish request, Principal principal) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.publish(number, id, request, UUID.fromString(principal.getName())));
    }

    @GetMapping("/versions/{id}")
    @Operation(operationId = "getPolicyRuleFile", summary = "등록된 규칙 파일 조회", description = "기존 원문 해시·연도·적용 기간을 유지한다.")
    public ResponseEntity<PolicyRuleActions.File> file(@PathVariable @Pattern(regexp = "[0-9]{20}") String number, @PathVariable UUID id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.file(number, id));
    }

    @ExceptionHandler({PolicyRuleActions.Invalid.class, MethodArgumentNotValidException.class, HttpMessageNotReadableException.class,
            HandlerMethodValidationException.class, MethodArgumentTypeMismatchException.class})
    ResponseEntity<PolicyApiError> invalid() { return error(400, "INVALID_POLICY_RULE", "규칙 파일의 형식·정책번호·질문·기간과 사유를 확인해주세요."); }
    @ExceptionHandler({PolicyRuleActions.Changed.class, DuplicateKeyException.class})
    ResponseEntity<PolicyApiError> changed() { return error(409, "POLICY_RULE_CHANGED", "원문·기간·버전 또는 요청 정보가 바뀌었습니다. 최신 내용을 확인해주세요."); }
    @ExceptionHandler(PolicyRuleActions.Missing.class)
    ResponseEntity<PolicyApiError> missing() { return error(404, "POLICY_RULE_NOT_FOUND", "정책이나 규칙을 찾을 수 없습니다."); }
    @ExceptionHandler(DataAccessException.class)
    ResponseEntity<PolicyApiError> unavailable() { return error(503, "POLICY_RULE_UNAVAILABLE", "처리 결과를 확인하지 못했습니다. 같은 요청으로 다시 확인해주세요."); }
    private static ResponseEntity<PolicyApiError> error(int status, String code, String message) {
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(new PolicyApiError(code, message));
    }
}
