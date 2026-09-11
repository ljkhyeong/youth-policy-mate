package kr.youthpolicymate.policy.catalog;

import kr.youthpolicymate.eligibility.*;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import static kr.youthpolicymate.eligibility.ConditionAssessment.Outcome.*;
import static kr.youthpolicymate.policy.catalog.PolicyQuestions.*;

/** 청년 미래이음 대출의 기본요건을 비교하며 지원 제한·예외와 여신심사는 별도로 남긴다. */
public final class MisoYouthFutureRules {
    public static final String NUMBER = "20260421005400112773";
    public static final String CONTENT_HASH = "c2ba149dd238ced25aab441f1ff595079b0b2fb6b7d25f4d71a97a21ce3d3788";
    public static final String VERSION = "miso-youth-future-2026-v1";
    public static final String SOURCE = "https://www.kinfa.or.kr/financialProduct/youngFutureLinkLoan.do";
    public static final String SCOPE = "2026년 청년 미래이음 대출 기본 조건";
    private static final List<Option> CRITERION_OPTIONS = List.of(
            new Option("YES", "해당 · 기준 확인"), new Option("NO", "비해당 · 기준 확인"),
            new Option("UNKNOWN", "아직 확인하지 못했어요"));
    private static final List<String> ALTERNATIVE_IDS = List.of("credit", "welfare", "earnedIncomeCredit");
    private static final List<Question> QUESTIONS = List.of(
            new Question("age", "대출신청일 기준 만 나이는 어떻게 되나요?",
                    "만 19~34세가 대상이에요. 실제 신청일의 만 나이를 확인해주세요.",
                    List.of(new Option("AGE_19_TO_34", "만 19~34세"), new Option("UNDER_19", "만 19세 미만"),
                            new Option("OVER_34", "만 35세 이상"), new Option("UNKNOWN", "아직 확인하지 못했어요"))),
            new Question("employment", "미소금융 지점 기준으로 어떤 취·창업 상태인가요?",
                    "미취업 또는 취·창업 1년 이내 청년이 대상이에요. 근로·사업이 겹치거나 시작일·이력의 적용 기준이 불명확하면 확인 중을 선택해주세요.",
                    List.of(new Option("UNEMPLOYED", "미취업 · 대상 기준 확인"), new Option("EARLY_EMPLOYEE", "취업 1년 이내 · 기준 확인"),
                            new Option("EARLY_BUSINESS", "창업 1년 이내 · 기준 확인"), new Option("NOT_TARGET", "지점 확인 · 대상 아님"),
                            new Option("PENDING", "취·창업 이력 확인 중"), new Option("UNKNOWN", "아직 확인하지 못했어요"))),
            new Question("credit", "개인신용평점 하위 20%에 해당하나요?",
                    "미소금융 지점에서 적용하는 신용평가 기준으로 확인해요. 앱의 점수만으로 추정하지 않아도 돼요. 아래 세 지원 요건 중 하나만 해당하면 돼요.", CRITERION_OPTIONS),
            new Question("welfare", "기초생활수급자 또는 차상위계층 이하에 해당하나요?",
                    "신청 시점의 수급·차상위 자격과 증빙을 확인해요. 소득이 적다는 이유만으로 해당한다고 판단하지 않아요.", CRITERION_OPTIONS),
            new Question("earnedIncomeCredit", "근로장려금 신청 자격 요건에 해당하나요?",
                    "미소금융 지점에서 인정하는 적용 연도와 자격·증빙 기준을 확인해요. 과거 수령 여부나 근로소득 유무만으로 판단하지 않아요.", CRITERION_OPTIONS));

    public static boolean appliesAt(Instant now) {
        var date = now.atZone(ZoneId.of("Asia/Seoul")).toLocalDate();
        return !date.isBefore(LocalDate.of(2026, 3, 31)) && date.getYear() == 2026;
    }

    public static Questionnaire questionnaire(long revision) {
        return new Questionnaire(NUMBER, revision, VERSION, true, SCOPE,
                "연령·취창업 상태와 세 지원 요건 중 하나에 해당하는지 확인해요. 자금 용도와 지원 제한·예외, 최종 여신심사는 지점에서 확인해야 해요.", SOURCE, QUESTIONS);
    }

    public static Evaluation evaluate(long revision, Request input, Instant now) {
        if (!appliesAt(now)) throw new IllegalArgumentException("검토한 2026년 3월 31일 이후 기준만 사용할 수 있습니다.");
        var values = validatedAnswers(QUESTIONS, input.answers());
        var alternatives = ALTERNATIVE_IDS.stream().map(id -> values.getOrDefault(id, "UNKNOWN")).toList();
        var alternativeOutcome = alternatives.contains("YES") ? MET : alternatives.stream().allMatch("NO"::equals) ? NOT_MET : UNKNOWN;
        var checks = List.of(
                ageCheck(values.getOrDefault("age", "UNKNOWN"), provided("age", values)),
                check("취·창업 상태", provided("employment", values), switch (values.getOrDefault("employment", "UNKNOWN")) {
                    case "UNEMPLOYED", "EARLY_EMPLOYEE", "EARLY_BUSINESS" -> MET;
                    case "NOT_TARGET" -> NOT_MET;
                    default -> UNKNOWN;
                }, "미취업 또는 취·창업 1년 이내 청년이 대상이에요.",
                        "취·창업 이력과 기간의 적용 기준을 미소금융 지점에서 확인해주세요."),
                new Check("지원 요건 중 하나 충족", "신용평점: " + provided("credit", values) + " / 수급·차상위: "
                        + provided("welfare", values) + " / 근로장려금: " + provided("earnedIncomeCredit", values), alternativeOutcome,
                        alternativeOutcome == MET ? "세 요건 중 하나 이상이 확인됐어요. 나머지 요건은 모두 충족할 필요가 없어요."
                                : alternativeOutcome == NOT_MET ? "세 요건 모두 비해당으로 확인됐어요."
                                : "아직 확인하지 못한 요건이 있어요. 나머지를 확인하기 전에는 비해당으로 판단하지 않아요.",
                        "개인신용평점 하위 20%, 기초생활수급자·차상위계층 이하, 근로장려금 신청 자격 중 하나에 해당해야 해요."));
        var evidence = new SourceEvidence(SOURCE, SCOPE, Optional.empty());
        var conditions = new ArrayList<ConditionAssessment>();
        for (int i = 0; i < checks.size(); i++) {
            var check = checks.get(i);
            conditions.add(new ConditionAssessment("miso-youth-future-" + i, check.label(), Optional.of(check.providedValue()), Optional.empty(),
                    check.outcome(), check.outcome() == UNKNOWN ? Optional.of(ConditionAssessment.Uncertainty.MISSING_USER_INPUT) : Optional.empty(),
                    check.explanation(), evidence));
        }
        var basis = new EvaluationBasis(NUMBER, Long.toString(revision), VERSION, now);
        var common = new EligibilityDecision(basis, PolicyReview.complete(), conditions).status();
        var remaining = List.of(
                "사회진입을 위한 자금 용도와 상환 심사, 재무상담 연계·신청 서류를 미소금융 지점에서 확인해야 해요.",
                "신용정보 등재, 재산의 법적 절차, 국적·해외체류 등 지원 제한과 예외를 확인해야 해요. 성실상환·면책 예외가 있어 신용정보 등재만으로 단정하지 않아요.",
                "취·창업 기간과 신용평점·수급·근로장려금 자격의 적용 기준 및 증빙을 확인해주세요.",
                "최종 대출 여부·금액·금리·기간은 여신심사에서 정해져요. 햇살론유스 이용 이력만으로 중복 이용 불가로 판단하지 않아요.");
        var whole = new EligibilityDecision(basis, PolicyReview.incomplete(remaining.stream()
                .map(message -> new PolicyReview.PendingIssue(message, evidence)).toList()), conditions);
        return new Evaluation(NUMBER, revision, VERSION, whole.status(), common, SCOPE,
                "입력한 기본 조건의 비교 결과예요. 지원 제한과 예외, 최종 여신심사는 미소금융 지점에서 확인해야 해요.",
                remaining, SOURCE, now, checks);
    }

    static Check ageCheck(LocalDate birthDate, Instant now) {
        var today = now.atZone(ZoneId.of("Asia/Seoul")).toLocalDate();
        var age = Period.between(birthDate, today).getYears();
        return ageCheck(age < 19 ? "UNDER_19" : age <= 34 ? "AGE_19_TO_34" : "OVER_34", "만 " + age + "세 (" + today + " · 서울)");
    }

    private static Check ageCheck(String value, String provided) {
        return check("대출신청일 연령", provided, switch (value) {
            case "AGE_19_TO_34" -> MET;
            case "UNDER_19", "OVER_34" -> NOT_MET;
            default -> UNKNOWN;
        }, "대출신청일 기준 만 19~34세가 대상이에요.", "실제 대출신청일의 만 나이를 확인해주세요.");
    }

    private static String provided(String id, Map<String, String> values) {
        return QUESTIONS.stream().filter(q -> q.id().equals(id)).flatMap(q -> q.options().stream())
                .filter(option -> option.value().equals(values.get(id))).map(Option::label).findFirst().orElse("미응답");
    }

    private static Check check(String label, String provided, ConditionAssessment.Outcome outcome, String evidence, String unknownReason) {
        return new Check(label, provided, outcome, outcome == MET ? "입력한 답변은 이 조건을 충족해요."
                : outcome == NOT_MET ? "입력한 답변은 이 조건을 충족하지 않아요." : unknownReason, evidence);
    }
}
