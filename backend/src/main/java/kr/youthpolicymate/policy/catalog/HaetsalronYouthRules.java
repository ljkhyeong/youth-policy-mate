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

/** 보증 공통요건의 일부를 비교하며 서민금융진흥원 보증심사와 은행 대출심사는 별도로 남긴다. */
public final class HaetsalronYouthRules {
    public static final String NUMBER = "20260724005400113307";
    public static final String CONTENT_HASH = "1fbde72fe6ad25caf843889a0571a62c817ddd8b246df04093bd65710eb22df5";
    public static final String VERSION = "haetsalron-youth-2026-v1";
    public static final String SOURCE = "https://www.kinfa.or.kr/financialProduct/hessalLoanYoos.do";
    public static final String SCOPE = "2026년 햇살론유스 보증 공통 조건";
    private static final List<Question> QUESTIONS = List.of(
            new Question("age", "보증신청일 기준 만 나이는 어떻게 되나요?",
                    "만 19~34세가 대상이에요. 군입대 예정자의 거치기간 연장은 신청 연령 연장과 달라요.",
                    List.of(new Option("AGE_19_TO_34", "만 19~34세"), new Option("UNDER_19", "만 19세 미만"),
                            new Option("OVER_34", "만 35세 이상"), new Option("UNKNOWN", "모르겠어요"))),
            new Question("applicantType", "서민금융진흥원 기준으로 어떤 이용 대상에 해당하나요?",
                    "대학(원)생·학점은행제 수강자·미취업청년은 취업준비생 유형이에요. 학적·근로·사업이 겹치거나 기간이 불명확하면 적용 유형을 확인해주세요.",
                    List.of(new Option("PREPARING_CONFIRMED", "취업준비생 · 학적·근로 기준 확인"),
                            new Option("EARLY_EMPLOYEE", "사회초년생 · 중소기업 1년 이하"),
                            new Option("YOUNG_BUSINESS", "청년사업자 · 창업 1년 이하"),
                            new Option("NOT_TARGET_CONFIRMED", "서민금융진흥원 확인 · 대상 아님"),
                            new Option("PENDING", "학적·근로·사업 기준 확인 중"),
                            new Option("UNKNOWN", "모르겠어요"))),
            new Question("incomeBasis", "보증신청용 소득 기준을 확인했나요?",
                    "서민금융진흥원에서 요구하는 증빙과 산정 기간을 확인해요. 무소득·단기 근로·사업소득의 인정 여부가 확인 중이면 그대로 선택해주세요.",
                    List.of(new Option("CONFIRMED", "증빙·소득 산정 기준 확인"), new Option("PENDING", "증빙·적용 기준 확인 중"),
                            new Option("UNKNOWN", "모르겠어요"))),
            new Question("annualIncome", "보증신청 기준으로 확인한 본인 연소득은 얼마인가요?",
                    "본인의 연소득을 확인해요. 월급·사업 매출·가구소득을 그대로 넣거나 임의로 연환산하지 마세요.",
                    List.of(new Option("UP_TO_35M", "3,500만 원 이하"), new Option("OVER_35M", "3,500만 원 초과"),
                            new Option("UNKNOWN", "모르겠어요"))),
            new Question("lifetimeLimit", "서민금융진흥원에서 확인한 생애 보증한도가 남아 있나요?",
                    "생애 한도는 1,200만 원이며 갚아도 복원되지 않아요. 현재 대출 잔액이나 이번에 빌릴 수 있는 금액과 구분해주세요.",
                    List.of(new Option("REMAINING_CONFIRMED", "남은 생애 한도 있음"), new Option("EXHAUSTED_CONFIRMED", "생애 한도 전액 사용"),
                            new Option("PENDING", "이용 이력·남은 한도 확인 중"), new Option("UNKNOWN", "모르겠어요"))));

    public static boolean appliesAt(Instant now) { return now.atZone(ZoneId.of("Asia/Seoul")).getYear() == 2026; }

    public static Questionnaire questionnaire(long revision) {
        return new Questionnaire(NUMBER, revision, VERSION, true, SCOPE,
                "연령·이용 대상·소득·남은 생애 보증한도를 확인해요. 조건이 맞아도 서민금융진흥원의 보증심사와 은행의 대출심사가 필요해요.", SOURCE, QUESTIONS);
    }

    public static Evaluation evaluate(long revision, Request input, Instant now) {
        if (!appliesAt(now)) throw new IllegalArgumentException("검토한 2026년 보증 기준만 사용할 수 있습니다.");
        var values = validatedAnswers(QUESTIONS, input.answers());
        var type = values.getOrDefault("applicantType", "UNKNOWN");
        var income = values.getOrDefault("annualIncome", "UNKNOWN");
        var limit = values.getOrDefault("lifetimeLimit", "UNKNOWN");
        var incomeOutcome = UNKNOWN;
        if ("CONFIRMED".equals(values.get("incomeBasis"))) {
            incomeOutcome = switch (income) { case "UP_TO_35M" -> MET; case "OVER_35M" -> NOT_MET; default -> UNKNOWN; };
        }
        var checks = List.of(
                ageCheck(values.getOrDefault("age", "UNKNOWN"), provided("age", values)),
                check("이용 대상", provided("applicantType", values), switch (type) {
                    case "PREPARING_CONFIRMED", "EARLY_EMPLOYEE", "YOUNG_BUSINESS" -> MET;
                    case "NOT_TARGET_CONFIRMED" -> NOT_MET;
                    default -> UNKNOWN;
                }, "취업준비생, 중소기업에 1년 이하 재직한 사회초년생, 창업 1년 이하인 청년 개인사업자가 대상이에요.",
                        "학적·재직·사업 이력과 적용 유형을 서민금융진흥원 기준으로 확인해주세요. 현재 취업상태만으로 판단하지 않아요."),
                check("본인 연소득", provided("incomeBasis", values) + " / " + provided("annualIncome", values), incomeOutcome,
                        "서민금융진흥원 기준으로 확인한 본인 연소득이 3,500만 원 이하여야 해요.",
                        "소득 증빙·산정 기간과 금액을 확인해주세요. 기준이 미확인이면 금액만으로 비교하지 않아요."),
                check("남은 생애 보증한도", provided("lifetimeLimit", values),
                        "REMAINING_CONFIRMED".equals(limit) ? MET : "EXHAUSTED_CONFIRMED".equals(limit) ? NOT_MET : UNKNOWN,
                        "동일인 생애 보증한도는 1,200만 원이에요. 이미 이용한 금액은 상환해도 한도가 복원되지 않아요.",
                        "서민금융진흥원에서 이용 이력과 남은 생애 보증한도를 확인해주세요. 대출 잔액으로 계산하지 않아요."));
        var evidence = new SourceEvidence(SOURCE, SCOPE, Optional.empty());
        var conditions = new ArrayList<ConditionAssessment>();
        for (int i = 0; i < checks.size(); i++) {
            var check = checks.get(i);
            conditions.add(new ConditionAssessment("haetsalron-youth-" + i, check.label(), Optional.of(check.providedValue()), Optional.empty(), check.outcome(),
                    check.outcome() == UNKNOWN ? Optional.of(ConditionAssessment.Uncertainty.MISSING_USER_INPUT) : Optional.empty(), check.explanation(), evidence));
        }
        var basis = new EvaluationBasis(NUMBER, Long.toString(revision), VERSION, now);
        var common = new EligibilityDecision(basis, PolicyReview.complete(), conditions).status();
        var remaining = List.of("신분·학적·재직·사업기간·소득 증빙과 재산 보유 등 보증 제외 사유는 서민금융진흥원에서 확인해야 해요.",
                "학점은행제 수강·학점 인정, 단기 근로·겸업 등의 적용 유형은 서민금융진흥원에서 확인해주세요.",
                "남은 생애 한도가 있어도 기간별·용도별 한도, 재신청 간격과 자금 용도 증빙을 별도로 확인해야 해요.",
                "금융교육과 보증심사·은행 대출심사가 필요해요. 실제 승인 여부·대출액·금리·보증료·상환 조건은 심사 과정에서 확인해주세요.");
        var whole = new EligibilityDecision(basis, PolicyReview.incomplete(remaining.stream()
                .map(message -> new PolicyReview.PendingIssue(message, evidence)).toList()), conditions);
        return new Evaluation(NUMBER, revision, VERSION, whole.status(), common, SCOPE,
                "연령·이용 대상·소득·생애 보증한도의 확인 결과예요. 실제 보증·대출 승인과 이용 조건은 별도 심사가 필요해요.",
                remaining, SOURCE, now, checks);
    }

    static Check ageCheck(LocalDate birthDate, Instant now) {
        var today = now.atZone(ZoneId.of("Asia/Seoul")).toLocalDate();
        var age = Period.between(birthDate, today).getYears();
        return ageCheck(age < 19 ? "UNDER_19" : age <= 34 ? "AGE_19_TO_34" : "OVER_34",
                "만 " + age + "세 (" + today + " · 서울)");
    }

    private static Check ageCheck(String age, String provided) {
        return check("보증신청일 연령", provided, switch (age) {
            case "AGE_19_TO_34" -> MET;
            case "UNDER_19", "OVER_34" -> NOT_MET;
            default -> UNKNOWN;
        }, "보증신청일 기준 만 19~34세가 대상이에요. 군입대 예정자의 추가 거치기간은 연령 상한 연장이 아니에요.",
                "실제 보증신청일의 만 나이를 확인해주세요.");
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
