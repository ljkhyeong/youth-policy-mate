package kr.youthpolicymate.policy.catalog;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
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
    public PolicyCatalogController(PolicyCatalogStore store) { this.store = store; }

    @GetMapping
    @Operation(operationId = "listPolicies", summary = "저장된 정책 목록과 제목·설명 검색")
    @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = PolicyListResponse.class)))
    @ApiResponse(responseCode = "400", content = @Content(schema = @Schema(implementation = PolicyApiError.class)))
    public PolicyListResponse list(@RequestParam(defaultValue = "") @Size(max = 80) String q,
                                   @RequestParam(defaultValue = "1") @Min(1) @Max(1000) int page,
                                   @RequestParam(defaultValue = "20") @Min(1) @Max(50) int pageSize) {
        return store.list(q.strip(), page, pageSize);
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
