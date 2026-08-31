package kr.youthpolicymate.devpreview;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

import static kr.youthpolicymate.devpreview.EligibilityTrialRequest.*;

@RestController
@Profile("preview")
public class EligibilityTrialController {
    @GetMapping(value = "/api/dev/eligibility-trial", produces = "application/json")
    @Operation(operationId = "getDevelopmentEligibilityQuestions", summary = "인공 재판정 질문과 답변 선택지 조회")
    public ResponseEntity<EligibilityQuestionsResponse> questions() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new EligibilityQuestionsResponse(
                EligibilityExamplesResponse.DataKind.SYNTHETIC,
                Arrays.stream(QuestionSetId.values()).map(id -> new SyntheticEligibilityPolicy(id).question()).toList(),
                List.of(new EligibilityQuestionsResponse.EmploymentChoiceResponse(EmploymentChoice.UNANSWERED, "선택 안 함"),
                        new EligibilityQuestionsResponse.EmploymentChoiceResponse(EmploymentChoice.APPLIES, "해당함"),
                        new EligibilityQuestionsResponse.EmploymentChoiceResponse(EmploymentChoice.DOES_NOT_APPLY, "해당하지 않음"),
                        new EligibilityQuestionsResponse.EmploymentChoiceResponse(EmploymentChoice.UNKNOWN, "모름")),
                List.of(new EligibilityQuestionsResponse.IncomeChoiceResponse(IncomeChoice.UNANSWERED, "선택 안 함"),
                        new EligibilityQuestionsResponse.IncomeChoiceResponse(IncomeChoice.UNKNOWN, "모름"),
                        new EligibilityQuestionsResponse.IncomeChoiceResponse(IncomeChoice.ZERO, "0원"),
                        new EligibilityQuestionsResponse.IncomeChoiceResponse(IncomeChoice.UP_TO_20M, "0원 초과 ~ 20,000,000원 이하"),
                        new EligibilityQuestionsResponse.IncomeChoiceResponse(IncomeChoice.BETWEEN_20M_30M, "20,000,000원 초과 ~ 30,000,000원 이하"),
                        new EligibilityQuestionsResponse.IncomeChoiceResponse(IncomeChoice.BETWEEN_20M_25M, "20,000,000원 초과 ~ 25,000,000원 이하 · 좁힌 구간"),
                        new EligibilityQuestionsResponse.IncomeChoiceResponse(IncomeChoice.OVER_25M, "25,000,000원 초과"))));
    }

    @PostMapping(value = "/api/dev/eligibility-trial", consumes = "application/json", produces = "application/json")
    @Operation(operationId = "evaluateDevelopmentEligibilityAnswers", summary = "인공 답변으로 자격 재판정",
            description = "정해진 질문 버전·답변 코드만 받는다. 실제 개인정보·저장·외부 호출 없이 계산한다. 답변의 질문 버전이 다르면 기존 비교기가 재확인을 요구한다.")
    @ApiResponse(responseCode = "200", description = "인공 답변의 계산 결과")
    @ApiResponse(responseCode = "400", description = "필수 값 누락 또는 지원하지 않는 답변 코드", content = @Content(schema = @Schema(implementation = InputError.class)))
    public ResponseEntity<EligibilityTrialResponse> evaluate(@Valid @RequestBody EligibilityTrialRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new SyntheticEligibilityPolicy(request.questionSet()).evaluate(request));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
    ResponseEntity<InputError> invalidInput() {
        return ResponseEntity.badRequest().cacheControl(CacheControl.noStore())
                .body(new InputError("INVALID_SYNTHETIC_ANSWER", "인공 질문 버전과 정해진 답변 코드를 확인해주세요."));
    }

    @Schema(name = "TrialInputError", requiredProperties = {"code", "message"})
    public record InputError(String code, String message) {}
}
