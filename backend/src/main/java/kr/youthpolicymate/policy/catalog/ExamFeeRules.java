package kr.youthpolicymate.policy.catalog;

import kr.youthpolicymate.eligibility.*;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import static kr.youthpolicymate.eligibility.ConditionAssessment.Outcome.*;
import static kr.youthpolicymate.policy.catalog.PolicyQuestions.*;

/** 공식 2026년 안내의 지원 공통요건을 비교한다. 시험 응시자격과 실시간 예산은 별도 확인한다. */
public final class ExamFeeRules {
    public static final String NUMBER = "20260527005400113224";
    public static final String CONTENT_HASH = "7d7880c52f4f696225afd12d0871c41bf155ab568dd7b72f3d34b7f76b800d56";
    public static final String VERSION = "exam-fee-2026-v1";
    public static final String SOURCE = "https://hrdc.hrdkorea.or.kr/hrdc/196105";
    public static final String SCOPE = "2026년 청년 국가기술자격 응시료 지원 조건";
    private static final List<Question> QUESTIONS = List.of(
            new Question("birthRange", "출생일이 어느 범위에 해당하나요?",
                    "오늘의 만 나이 대신 2026년 공식 안내의 출생일 기준을 사용해요. 생년월일 전체는 입력하지 않아요.",
                    List.of(new Option("ON_OR_AFTER_1991_01_01", "1991년 1월 1일 또는 그 이후"),
                            new Option("BEFORE_1991_01_01", "1990년 12월 31일 또는 그 이전"), new Option("UNKNOWN", "모르겠어요"))),
            new Question("exam", "응시할 시험의 종류와 시행기관을 확인했나요?",
                    "한국산업인력공단에서 시행하는 국가기술자격시험이 대상이에요. 큐넷에 보이는 모든 시험이 대상인 것은 아니에요.",
                    List.of(new Option("HRDK_TECHNICAL", "한국산업인력공단 시행 국가기술자격시험으로 확인했어요"),
                            new Option("OTHER", "다른 기관의 시험이거나 국가전문·민간자격 시험이에요"), new Option("UNKNOWN", "아직 확인하지 못했어요"))),
            new Question("remainingUses", "큐넷에서 확인한 2026년 남은 응시료 지원 횟수는 얼마인가요?",
                    "취소 후 횟수 복구가 아직 반영되지 않았다면 ‘복구 확인 중’을 선택해주세요. 시험에 가지 않은 것만으로는 횟수가 복구되지 않아요.",
                    List.of(new Option("ONE", "1회"), new Option("TWO", "2회"), new Option("THREE", "3회"), new Option("ZERO", "0회 · 복구 대기 건도 없어요"),
                            new Option("RESTORING", "원서접수 취소 후 복구 확인 중이에요"), new Option("UNKNOWN", "아직 확인하지 못했어요"))));

    public static boolean appliesAt(Instant now) { return now.atZone(ZoneId.of("Asia/Seoul")).getYear() == 2026; }
    public static Questionnaire questionnaire(long revision) {
        return new Questionnaire(NUMBER, revision, VERSION, true, SCOPE,
                "출생일·시험 종류·남은 지원 횟수를 확인해요. 시험 응시자격과 예산 소진 여부와 할인 적용는 큐넷에서 확인해주세요.", SOURCE, QUESTIONS);
    }
    public static Evaluation evaluate(long revision, Request input, Instant now) {
        if (!appliesAt(now)) throw new IllegalArgumentException("검토한 2026년 지원 기준만 사용할 수 있습니다.");
        var allowed = QUESTIONS.stream().collect(Collectors.toMap(Question::id, q -> q.options().stream().map(Option::value).toList()));
        var values = new HashMap<String, String>();
        for (var answer : input.answers()) {
            if (answer == null || !allowed.containsKey(answer.questionId()) || !allowed.get(answer.questionId()).contains(answer.value())
                    || values.putIfAbsent(answer.questionId(), answer.value()) != null) throw new IllegalArgumentException("질문과 답변을 다시 확인해주세요.");
        }
        var birth = values.get("birthRange"); var exam = values.get("exam"); var uses = values.get("remainingUses");
        var checks = List.of(
                check("birthRange", "공식 출생일 기준", birth, "ON_OR_AFTER_1991_01_01".equals(birth) ? MET : "BEFORE_1991_01_01".equals(birth) ? NOT_MET : UNKNOWN,
                        "2026년 지원 대상은 1991년 1월 1일 이후 출생자예요."),
                check("exam", "시험 종류와 시행기관", exam, "HRDK_TECHNICAL".equals(exam) ? MET : "OTHER".equals(exam) ? NOT_MET : UNKNOWN,
                        "이 지원은 한국산업인력공단이 시행하는 국가기술자격시험의 응시료에 적용돼요."),
                check("remainingUses", "2026년 남은 지원 횟수", uses, List.of("ONE", "TWO", "THREE").contains(uses == null ? "" : uses) ? MET : "ZERO".equals(uses) ? NOT_MET : UNKNOWN,
                        "해당 연도 최대 3회예요. 원서접수 취소 후 차감 횟수는 복구되지만 시험 미응시만으로는 복구되지 않아요. 실제 반영 여부는 큐넷에서 확인해요."));
        var evidence = new SourceEvidence(SOURCE, SCOPE, Optional.empty());
        var conditions = new ArrayList<ConditionAssessment>();
        for (int i = 0; i < checks.size(); i++) {
            var check = checks.get(i);
            conditions.add(new ConditionAssessment("exam-fee-" + i, check.label(), Optional.of(check.providedValue()), Optional.empty(), check.outcome(),
                    check.outcome() == UNKNOWN ? Optional.of(ConditionAssessment.Uncertainty.MISSING_USER_INPUT) : Optional.empty(), check.explanation(), evidence));
        }
        var basis = new EvaluationBasis(NUMBER, Long.toString(revision), VERSION, now);
        var common = new EligibilityDecision(basis, PolicyReview.complete(), conditions).status();
        var remaining = List.of("해당 시험의 응시자격·원서접수 일정은 별도로 확인해주세요.",
                "지원은 2026년 1월 6일부터 예산 소진 전까지예요. 남은 횟수가 있어도 예산 소진 시 지원되지 않으며, 이 서비스는 현재 예산을 조회하지 않아요.",
                "공식 안내는 원서접수 때 지원 자동 적용과 ‘지원받지 않기’ 선택을 설명해요. 결제 전 실제 할인 금액과 횟수 차감을 큐넷에서 확인해주세요.");
        var whole = new EligibilityDecision(basis, PolicyReview.incomplete(remaining.stream()
                .map(message -> new PolicyReview.PendingIssue(message, evidence)).toList()), conditions);
        return new Evaluation(NUMBER, revision, VERSION, whole.status(), common, SCOPE,
                "출생일·시험 종류·남은 지원 횟수의 확인 결과예요. 시험 접수와 예산 소진 여부와 할인 적용는 별도로 확인해주세요.", remaining, SOURCE, now, checks);
    }
    private static Check check(String id, String label, String value, ConditionAssessment.Outcome outcome, String evidence) {
        var provided = QUESTIONS.stream().filter(q -> q.id().equals(id)).flatMap(q -> q.options().stream())
                .filter(option -> option.value().equals(value)).map(Option::label).findFirst().orElse("미응답");
        String explanation = outcome == MET ? "입력한 답변은 이 조건을 충족해요."
                : outcome == NOT_MET ? "입력한 답변은 이 조건을 충족하지 않아요."
                : "RESTORING".equals(value) ? "큐넷에서 지원 횟수가 복구됐는지 확인한 뒤 다시 답해주세요."
                : "이 항목을 확인한 뒤 다시 답해주세요.";
        return new Check(label, provided, outcome, explanation, evidence);
    }
}
