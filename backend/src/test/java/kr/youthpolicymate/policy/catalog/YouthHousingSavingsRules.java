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

/** 이전 결과 비교용 고정 자료. 가입 시 연령·본인 무주택·소득을 비교한다. 통장 전환과 금리·세제·대출 심사는 별도 확인한다. */
public final class YouthHousingSavingsRules {
    public static final String NUMBER = "20260616005400113238";
    public static final String CONTENT_HASH = "52ab8f797a2bd470745ded339b0b789ea6baf46e44177f4699e6d60fffc8d214";
    public static final String VERSION = "youth-housing-2026-v1";
    public static final String SOURCE = "https://obank.kbstar.com/quics?cc=b061761%3Ab061770&isNew=N&page=C020702&prcode=DP01000935";
    public static final String SCOPE = "2026년 청년주택드림청약통장 가입 조건";
    private static final List<Question> QUESTIONS = List.of(
            new Question("age", "가입일 기준 만 나이는 어떻게 되나요?",
                    "만 35세 이상은 인정되는 병역기간을 최대 6년 차감할 수 있어요. 차감 후 만 34세 이하인지 은행에서 확인해주세요.",
                    List.of(new Option("AGE_19_TO_34", "만 19~34세"),
                            new Option("MILITARY_AGE_CONFIRMED", "은행 확인 · 차감 후 만 34세 이하"),
                            new Option("UNDER_19", "만 19세 미만"),
                            new Option("NO_MILITARY_DEDUCTION", "만 35세 이상 · 차감 대상 아님"),
                            new Option("OVER_LIMIT_CONFIRMED", "차감 후에도 만 35세 이상"),
                            new Option("MILITARY_AGE_PENDING", "병역기간 차감 기준 확인 중"),
                            new Option("UNKNOWN", "모르겠어요"))),
            new Question("homeOwnership", "가입일에 본인 소유의 주택이 없나요?",
                    "가입 조건은 본인의 무주택 여부예요. 소유 여부가 불분명하면 은행에서 확인해주세요.",
                    List.of(new Option("NO_HOME", "본인 소유의 주택이 없어요"), new Option("OWNS_HOME", "본인 소유의 주택이 있어요"),
                            new Option("UNKNOWN", "모르겠어요"))),
            new Question("incomeBasis", "가입용 소득서류의 기준을 확인했나요?",
                    "원칙은 직전 과세연도인 2025년이에요. 소득이 아직 확정되지 않았거나 첫 취업·군복무 예외에 해당하면 은행에서 적용 기준을 확인해주세요.",
                    List.of(new Option("PREVIOUS_YEAR", "2025년 신고소득 서류 확인"),
                            new Option("EARLIER_YEAR_CONFIRMED", "은행 확인 · 2024년 소득 적용"),
                            new Option("ANNUALIZED_CONFIRMED", "은행 확인 · 첫 취업 소득을 연소득으로 환산"),
                            new Option("MILITARY_CONFIRMED", "은행 확인 · 군복무자 소득 예외"),
                            new Option("UNKNOWN", "모르겠어요"))),
            new Question("incomeAmount", "가입용 서류에서 확인한 연소득은 얼마인가요?",
                    "근로소득은 총급여액, 사업·기타소득은 종합소득금액 기준이에요. 월급·매출·가구소득을 입력하지 마세요. 군복무자 예외는 비과세 소득만 있는 경우예요.",
                    List.of(new Option("UP_TO_50M", "연 5,000만 원 이하"), new Option("OVER_50M", "연 5,000만 원 초과"),
                            new Option("TAX_EXEMPT_ONLY", "군복무 급여 등 비과세 소득만"), new Option("UNKNOWN", "모르겠어요"))));

    public static boolean appliesAt(Instant now) { return now.atZone(ZoneId.of("Asia/Seoul")).getYear() == 2026; }
    public static Questionnaire questionnaire(long revision) {
        return new Questionnaire(NUMBER, revision, VERSION, true, SCOPE,
                "가입 시 연령·본인 무주택·소득을 확인해요. 기존 통장 전환과 우대금리·비과세·대출은 은행에서 별도로 확인해주세요.", SOURCE, QUESTIONS);
    }
    public static Evaluation evaluate(long revision, Request input, Instant now) {
        if (!appliesAt(now)) throw new IllegalArgumentException("검토한 2026년 가입 기준만 사용할 수 있습니다.");
        var values = validatedAnswers(QUESTIONS, input.answers());
        var age = values.getOrDefault("age", "UNKNOWN");
        var home = values.getOrDefault("homeOwnership", "UNKNOWN");
        var checks = List.of(
                ageCheck(age, provided("age", values)),
                check("본인 무주택", provided("homeOwnership", values), "NO_HOME".equals(home) ? MET : "OWNS_HOME".equals(home) ? NOT_MET : UNKNOWN,
                        "가입일 기준 본인 소유의 주택이 없어야 해요. 세대주 여부와 세대원의 무주택 요건은 비과세 등 별도 기준에서 확인해요.",
                        "가입일에 본인 소유의 주택이 없는지 확인해주세요."),
                incomeCheck(values));
        var evidence = new SourceEvidence(SOURCE, SCOPE, Optional.empty());
        var conditions = new ArrayList<ConditionAssessment>();
        for (int i = 0; i < checks.size(); i++) {
            var check = checks.get(i);
            conditions.add(new ConditionAssessment("youth-housing-" + i, check.label(), Optional.of(check.providedValue()), Optional.empty(), check.outcome(),
                    check.outcome() == UNKNOWN ? Optional.of(ConditionAssessment.Uncertainty.MISSING_USER_INPUT) : Optional.empty(), check.explanation(), evidence));
        }
        var basis = new EvaluationBasis(NUMBER, Long.toString(revision), VERSION, now);
        var common = new EligibilityDecision(basis, PolicyReview.complete(), conditions).status();
        var remaining = List.of("국내 거주자 해당 여부와 나이·무주택·소득 증빙서류를 은행에서 확인해주세요.",
                "주택청약 계좌는 전 금융기관을 합쳐 1인 1계좌예요. 기존 통장이 있다면 신규 가입·전환 여부를 은행에서 확인해주세요.",
                "우대금리·비과세·소득공제는 각각 별도 조건을 확인해야 해요.",
                "청약 자격과 연계 대출의 신청 조건·심사는 별도로 확인해주세요.");
        var whole = new EligibilityDecision(basis, PolicyReview.incomplete(remaining.stream()
                .map(message -> new PolicyReview.PendingIssue(message, evidence)).toList()), conditions);
        return new Evaluation(NUMBER, revision, VERSION, whole.status(), common, SCOPE,
                "연령·본인 무주택·소득의 확인 결과예요. 가입 서류와 기존 통장 전환 여부는 은행에서 확인해주세요.", remaining, SOURCE, now, checks);
    }

    static Check ageCheck(LocalDate birthDate, Instant now) {
        var today = now.atZone(ZoneId.of("Asia/Seoul")).toLocalDate();
        var age = Period.between(birthDate, today).getYears();
        var answer = age < 19 ? "UNDER_19" : age <= 34 ? "AGE_19_TO_34" : "MILITARY_AGE_PENDING";
        return ageCheck(answer, "만 " + age + "세 (" + today + " · 서울)");
    }

    private static Check ageCheck(String age, String provided) {
        var outcome = switch (age) {
            case "AGE_19_TO_34", "MILITARY_AGE_CONFIRMED" -> MET;
            case "UNDER_19", "NO_MILITARY_DEDUCTION", "OVER_LIMIT_CONFIRMED" -> NOT_MET;
            default -> UNKNOWN;
        };
        return check("가입일 연령", provided, outcome,
                "만 19~34세가 대상이에요. 만 35세 이상은 인정되는 병역기간을 최대 6년 차감한 나이가 만 34세 이하여야 해요.",
                "가입일의 만 나이와 병역기간 차감 기준을 확인해주세요.");
    }

    private static Check incomeCheck(Map<String, String> values) {
        var basis = values.getOrDefault("incomeBasis", "UNKNOWN");
        var amount = values.getOrDefault("incomeAmount", "UNKNOWN");
        var outcome = switch (basis) {
            case "PREVIOUS_YEAR", "EARLIER_YEAR_CONFIRMED", "ANNUALIZED_CONFIRMED" -> switch (amount) {
                case "UP_TO_50M" -> MET;
                case "OVER_50M" -> NOT_MET;
                default -> UNKNOWN;
            };
            case "MILITARY_CONFIRMED" -> "TAX_EXEMPT_ONLY".equals(amount) ? MET : UNKNOWN;
            default -> UNKNOWN;
        };
        var unknownReason = "MILITARY_CONFIRMED".equals(basis)
                ? "군복무자 소득 예외는 비과세 소득만 있는 경우예요. 소득 종류와 적용 기간을 은행에서 다시 확인해주세요."
                : "가입용 소득서류의 기준 연도와 금액을 확인해주세요. 첫 취업·군복무 예외는 은행에서 확인해야 해요.";
        return check("가입 소득 기준", provided("incomeBasis", values) + " / " + provided("incomeAmount", values), outcome,
                "총급여액 또는 종합소득금액이 연 5,000만 원 이하여야 해요. 직전 연도 미확정·첫 취업은 서류 기준을 따르며, 군복무자는 비과세 소득만 있는 경우 복무 기간 등 예외 조건을 확인해요.", unknownReason);
    }
    private static String provided(String id, Map<String, String> values) {
        return QUESTIONS.stream().filter(q -> q.id().equals(id)).flatMap(q -> q.options().stream())
                .filter(option -> option.value().equals(values.get(id))).map(Option::label).findFirst().orElse("미응답");
    }
    private static Check check(String label, String provided, ConditionAssessment.Outcome outcome, String evidence, String unknownReason) {
        var explanation = outcome == MET ? "입력한 답변은 이 조건을 충족해요."
                : outcome == NOT_MET ? "입력한 답변은 이 조건을 충족하지 않아요." : unknownReason;
        return new Check(label, provided, outcome, explanation, evidence);
    }
}
