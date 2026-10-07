package kr.youthpolicymate.admin;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import kr.youthpolicymate.config.ApiException;
import kr.youthpolicymate.policy.catalog.PolicyApiError;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;

@RestController
@Profile("!preview")
@RequestMapping("/api/v1/admin/policy-rule-reviews")
@ApiResponse(responseCode = "200")
@ApiResponse(responseCode = "400", description = "조회 조건 오류", content = @Content(schema = @Schema(implementation = PolicyApiError.class)))
@ApiResponse(responseCode = "503", description = "조회 실패", content = @Content(schema = @Schema(implementation = PolicyApiError.class)))
class PolicyRuleReviewController {
    private final PolicyRuleReviewStore store;
    PolicyRuleReviewController(PolicyRuleReviewStore store) { this.store = store; }

    @GetMapping
    @Operation(operationId = "listPolicyRuleReviews", summary = "공고 조건 검토 목록",
            description = "원문 변경·기간 만료·조건 미등록 순서. 상태 필터와 검색을 전체 결과에 적용한 뒤 페이지를 나눈다.")
    public PolicyRuleReviews.Page list(@RequestParam(defaultValue = "1") @Min(1) @Max(1000) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int pageSize,
            @RequestParam(defaultValue = "REVIEW") PolicyRuleReviews.Filter filter,
            @RequestParam(defaultValue = "") @Size(max = 100) String query) {
        return store.list(page, pageSize, filter, query);
    }

    @GetMapping("/{number}")
    @Operation(operationId = "getPolicyRuleReview", summary = "공고 변경점·원본·질문 검토",
            description = "현재 공개 개정과 직전 개정을 비교한다. 직전 개정은 규칙의 검토 원문과 다를 수 있다. 조회로 규칙을 적용하지 않는다.")
    @ApiResponse(responseCode = "404", description = "정책 없음", content = @Content(schema = @Schema(implementation = PolicyApiError.class)))
    public PolicyRuleReviews.Detail detail(@PathVariable @Pattern(regexp = "[0-9]{1,100}") String number) {
        return store.detail(number).orElseThrow(ApiException::notFound);
    }
}
