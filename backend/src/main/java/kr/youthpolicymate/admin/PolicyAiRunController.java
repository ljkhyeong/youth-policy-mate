package kr.youthpolicymate.admin;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import kr.youthpolicymate.policy.catalog.PolicyApiError;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/policy-ai-runs")
@ApiResponse(responseCode = "200")
@ApiResponse(responseCode = "400", description = "조회 조건 오류", content = @Content(schema = @Schema(implementation = PolicyApiError.class)))
@ApiResponse(responseCode = "503", description = "조회 실패", content = @Content(schema = @Schema(implementation = PolicyApiError.class)))
class PolicyAiRunController {
    private final PolicyAiRunStore store;
    PolicyAiRunController(PolicyAiRunStore store) { this.store = store; }

    @GetMapping
    @Operation(operationId = "listPolicyAiRuns", summary = "AI 자동 추출 상태 조회",
            description = "요청별 마지막 자동 시도를 최근 순으로 조회한다. 검색·필터 후 페이지를 나누며 조회로 실행·재호출·정산하지 않는다.")
    public PolicyAiRuns.Page list(@RequestParam(defaultValue = "1") @Min(1) @Max(1000) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int pageSize,
            @RequestParam(defaultValue = "ALL") PolicyAiRuns.Filter filter,
            @RequestParam(defaultValue = "") @Size(max = 100) String query) {
        return store.list(page, pageSize, filter, query);
    }
}
