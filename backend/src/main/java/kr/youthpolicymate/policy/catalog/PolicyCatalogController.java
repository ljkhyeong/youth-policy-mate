package kr.youthpolicymate.policy.catalog;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import kr.youthpolicymate.policy.RecruitmentStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("!preview")
@RequestMapping("/api/v1/policies")
@ApiResponse(responseCode = "503", description = "정책 저장소 조회 실패", content = @Content(schema = @Schema(implementation = PolicyApiError.class)))
public class PolicyCatalogController {
    private final PolicyCatalogStore store;
    private final PolicyCheckService checks;
    private final java.time.Clock clock;
    public PolicyCatalogController(PolicyCatalogStore store, PolicyCheckService checks, java.time.Clock clock) {
        this.store = store; this.checks = checks; this.clock = clock;
    }

    @org.springframework.web.bind.annotation.PostMapping(value = "/checks", consumes = "application/json")
    @Operation(operationId = "checkPolicyConditions", summary = "기본 조건으로 연령 비교·정책 검색·접수 상태 필터·정렬. 조건은 저장하지 않음")
    @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = PolicyCheckResponse.class)))
    @ApiResponse(responseCode = "400", content = @Content(schema = @Schema(implementation = PolicyApiError.class)))
    public org.springframework.http.ResponseEntity<PolicyCheckResponse> check(
            @org.springframework.web.bind.annotation.RequestBody @jakarta.validation.Valid BasicConditions input,
            @RequestParam(defaultValue = "1") @Min(1) @Max(1000) int page,
            @RequestParam(defaultValue = "") @Size(max = 80) String q,
            @RequestParam(defaultValue = "AGE_MATCH") PolicyCheckResponse.Sort sort,
            @RequestParam(required = false) RecruitmentStatus recruitmentStatus) {
        return org.springframework.http.ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore()).body(checks.check(input, page, q.strip(), sort, recruitmentStatus));
    }

    @GetMapping
    @Operation(operationId = "listPolicies", summary = "정책 검색·질문 제공 여부·접수 상태 필터")
    @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = PolicyListResponse.class)))
    @ApiResponse(responseCode = "400", content = @Content(schema = @Schema(implementation = PolicyApiError.class)))
    public PolicyListResponse list(@RequestParam(defaultValue = "") @Size(max = 80) String q,
                                   @RequestParam(defaultValue = "1") @Min(1) @Max(1000) int page,
                                   @RequestParam(defaultValue = "20") @Min(1) @Max(50) int pageSize,
                                   @RequestParam(defaultValue = "false") boolean questionsOnly,
                                   @RequestParam(required = false) RecruitmentStatus recruitmentStatus) {
        return store.list(q.strip(), page, pageSize, questionsOnly, recruitmentStatus, clock.instant());
    }

    @GetMapping("/{policyNumber}")
    @Operation(operationId = "getPolicy", summary = "정책 상세와 원문 출처 조회")
    @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = PolicyDetailResponse.class)))
    @ApiResponse(responseCode = "404", content = @Content(schema = @Schema(implementation = PolicyApiError.class)))
    public PolicyDetailResponse detail(@PathVariable String policyNumber) {
        if (!policyNumber.matches("[0-9]{1,100}")) throw new PolicyNotFoundException();
        return store.find(policyNumber).orElseThrow(PolicyNotFoundException::new);
    }
}
