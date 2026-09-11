package kr.youthpolicymate.policy.catalog;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@Profile("!preview")
@RequestMapping("/api/v1/policies/{number}")
@ApiResponse(responseCode = "404", description = "정책 없음", content = @Content(schema = @Schema(implementation = PolicyApiError.class)))
@ApiResponse(responseCode = "503", description = "정책 저장소 조회 실패", content = @Content(schema = @Schema(implementation = PolicyApiError.class)))
public class PolicyQuestionController {
    private final PolicyQuestionService service;
    public PolicyQuestionController(PolicyQuestionService service) { this.service = service; }
    @GetMapping("/questions")
    @Operation(operationId = "getPolicyQuestions", summary = "현재 정책 개정에서 검토한 추가 질문 조회")
    @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = PolicyQuestions.Questionnaire.class)))
    public ResponseEntity<PolicyQuestions.Questionnaire> questions(@PathVariable String number) { return privateResponse(service.questions(number)); }
    @PostMapping(value = "/evaluation", consumes = "application/json")
    @Operation(operationId = "evaluatePolicyAnswers", summary = "추가 답변으로 확인한 공통요건 비교. 답변은 저장하지 않음")
    @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = PolicyQuestions.Evaluation.class)))
    @ApiResponse(responseCode = "400", description = "질문·답변 형식 오류", content = @Content(schema = @Schema(implementation = PolicyApiError.class)))
    @ApiResponse(responseCode = "409", description = "정책 개정 또는 질문 변경", content = @Content(schema = @Schema(implementation = PolicyApiError.class)))
    public ResponseEntity<PolicyQuestions.Evaluation> evaluate(@PathVariable String number, @RequestBody @Valid PolicyQuestions.Request input) {
        return privateResponse(service.evaluate(number, input));
    }
    @PostMapping(value = "/question-prefill", consumes = "application/json")
    @Operation(operationId = "prefillPolicyAnswers", summary = "확인한 생년월일을 현재 공고의 출생일 답변으로 변환. 저장하지 않음")
    @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = PolicyQuestions.Prefill.class)))
    @ApiResponse(responseCode = "400", description = "생년월일 형식 오류", content = @Content(schema = @Schema(implementation = PolicyApiError.class)))
    @ApiResponse(responseCode = "409", description = "정책 개정 또는 질문 변경", content = @Content(schema = @Schema(implementation = PolicyApiError.class)))
    public ResponseEntity<PolicyQuestions.Prefill> prefill(@PathVariable String number, @RequestBody @Valid PolicyQuestions.PrefillRequest input) {
        return privateResponse(service.prefill(number, input));
    }
    private <T> ResponseEntity<T> privateResponse(T body) { return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body); }
}
