package kr.youthpolicymate.admin;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import kr.youthpolicymate.member.CurrentMember;
import kr.youthpolicymate.policy.catalog.PolicyApiError;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@Profile("!preview")
@RequestMapping("/api/v1/admin/policy-rule-reviews/{number}")
@ApiResponse(responseCode = "200")
@ApiResponse(responseCode = "400", description = "규칙·사유 입력 오류", content = @Content(schema = @Schema(implementation = PolicyApiError.class)))
@ApiResponse(responseCode = "404", description = "정책·규칙 없음", content = @Content(schema = @Schema(implementation = PolicyApiError.class)))
@ApiResponse(responseCode = "409", description = "원문·기간·적용 버전·요청 정보 변경", content = @Content(schema = @Schema(implementation = PolicyApiError.class)))
@ApiResponse(responseCode = "503", description = "처리 결과 미확인", content = @Content(schema = @Schema(implementation = PolicyApiError.class)))
class PolicyRuleManagementController {
    private final PolicyRuleManagementService service;
    PolicyRuleManagementController(PolicyRuleManagementService service) { this.service = service; }

    @PostMapping("/drafts")
    @Operation(operationId = "createPolicyRuleDraft", summary = "검토한 규칙 파일을 새 초안으로 등록",
            description = "같은 요청 ID·입력·작업자는 기존 처리 결과를 반환한다. 초안 등록만으로 질문을 공개하지 않는다.")
    public PolicyRuleActions.Result draft(@PathVariable @Pattern(regexp = "[0-9]{20}") String number,
            @Valid @RequestBody PolicyRuleActions.Draft request, @CurrentMember UUID admin) {
        return service.draft(number, request, admin);
    }

    @PostMapping("/versions/{id}/publish")
    @Operation(operationId = "publishPolicyRuleDraft", summary = "검토한 초안을 현재 질문·판정에 적용",
            description = "조회한 개정·현재 적용 버전·원문·기간을 확인한다. 같은 요청 재시도는 기존 결과만 반환한다.")
    public PolicyRuleActions.Result publish(@PathVariable @Pattern(regexp = "[0-9]{20}") String number,
            @PathVariable UUID id, @Valid @RequestBody PolicyRuleActions.Publish request, @CurrentMember UUID admin) {
        return service.publish(number, id, request, admin);
    }

    @GetMapping("/versions/{id}")
    @Operation(operationId = "getPolicyRuleFile", summary = "등록된 규칙 파일 조회", description = "기존 원문 해시·연도·적용 기간을 유지한다.")
    public PolicyRuleActions.File file(@PathVariable @Pattern(regexp = "[0-9]{20}") String number, @PathVariable UUID id) {
        return service.file(number, id);
    }
}
