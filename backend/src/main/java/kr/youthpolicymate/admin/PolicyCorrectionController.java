package kr.youthpolicymate.admin;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import kr.youthpolicymate.config.ApiException;
import kr.youthpolicymate.member.CurrentMember;
import kr.youthpolicymate.policy.catalog.PolicyApiError;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/policy-corrections")
@ApiResponse(responseCode = "200")
@ApiResponse(responseCode = "400", description = "입력 오류", content = @Content(schema = @Schema(implementation = PolicyApiError.class)))
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
    public AdminSlice<PolicyCorrections.Item> list(@RequestParam(defaultValue = "1") @Min(1) @Max(1000) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int pageSize) {
        return service.list(page, pageSize);
    }

    @GetMapping("/policies/{number}")
    @Operation(operationId = "getCorrectionPolicy", summary = "보정할 공개 정책의 현재 내용 조회")
    @ApiResponse(responseCode = "404", description = "공개 정책 없음", content = @Content(schema = @Schema(implementation = PolicyApiError.class)))
    public CollectionExceptions.CurrentPolicy policy(@PathVariable @Pattern(regexp = "[0-9]{1,100}") String number) {
        return policies.currentPolicy(number).orElseThrow(ApiException::notFound);
    }

    @PostMapping
    @Operation(operationId = "createPolicyCorrection", summary = "공개 정책의 정책명 또는 운영 기관 보정",
            description = "정책당 한 항목을 보정하며 원본을 유지한다. 같은 요청 ID·입력·작업자는 기존 결과를 반환한다.")
    @ApiResponse(responseCode = "409", description = "개정 변경·진행 중 보정·요청 ID 충돌", content = @Content(schema = @Schema(implementation = PolicyApiError.class)))
    public PolicyCorrections.Item create(@Valid @RequestBody PolicyCorrections.Request request, @CurrentMember UUID admin) {
        return service.create(request, admin);
    }

    @PostMapping("/{id}/resolutions")
    @Operation(operationId = "resolvePolicyCorrection", summary = "정책 보정 해제 또는 새 원본 기준 보정 유지",
            description = "조회한 현재 개정과 검토 원본 ID가 같을 때만 처리한다. KEEP은 충돌 상태에서만 허용한다. 처리 후 남은 수집 실패 항목은 별도로 재처리한다.")
    @ApiResponse(responseCode = "409", description = "개정·검토 원본 변경 또는 요청 ID 충돌", content = @Content(schema = @Schema(implementation = PolicyApiError.class)))
    public PolicyCorrections.Item resolve(@PathVariable UUID id, @Valid @RequestBody PolicyCorrections.Resolution request, @CurrentMember UUID admin) {
        return service.resolve(id, request, admin);
    }
}
