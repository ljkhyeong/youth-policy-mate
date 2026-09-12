package kr.youthpolicymate.admin;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import kr.youthpolicymate.policy.catalog.PolicyApiError;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataAccessException;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestController
@Profile("!preview")
@RequestMapping("/api/v1/admin/policy-ai-runs")
@SecurityRequirement(name = "memberSession")
@ApiResponse(responseCode = "400", description = "조회 조건 오류", content = @Content(schema = @Schema(implementation = PolicyApiError.class)))
@ApiResponse(responseCode = "401", description = "로그인 필요", content = @Content(schema = @Schema(implementation = PolicyApiError.class)))
@ApiResponse(responseCode = "403", description = "관리자 권한 없음", content = @Content(schema = @Schema(implementation = PolicyApiError.class)))
@ApiResponse(responseCode = "503", description = "조회 실패", content = @Content(schema = @Schema(implementation = PolicyApiError.class)))
class PolicyAiRunController {
    private final PolicyAiRunStore store;
    PolicyAiRunController(PolicyAiRunStore store) { this.store = store; }

    @GetMapping
    @Operation(operationId = "listPolicyAiRuns", summary = "AI 자동 추출 상태 조회",
            description = "요청별 마지막 자동 시도를 최근 순으로 조회한다. 검색·필터 후 페이지를 나누며 조회로 실행·재호출·정산하지 않는다.")
    public ResponseEntity<PolicyAiRuns.Page> list(@RequestParam(defaultValue = "1") @Min(1) @Max(1000) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int pageSize,
            @RequestParam(defaultValue = "ALL") PolicyAiRuns.Filter filter,
            @RequestParam(defaultValue = "") @Size(max = 100) String query) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(store.list(page, pageSize, filter, query));
    }

    @ExceptionHandler({HandlerMethodValidationException.class, MethodArgumentTypeMismatchException.class})
    ResponseEntity<PolicyApiError> invalid() { return error(400, "INVALID_POLICY_AI_RUN_QUERY", "검색어·상태·페이지를 확인해주세요."); }

    @ExceptionHandler(DataAccessException.class)
    ResponseEntity<PolicyApiError> unavailable() { return error(503, "POLICY_AI_RUN_UNAVAILABLE", "AI 추출 내역을 불러오지 못했습니다."); }

    private static ResponseEntity<PolicyApiError> error(int status, String code, String message) {
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(new PolicyApiError(code, message));
    }
}
