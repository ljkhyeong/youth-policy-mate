package kr.youthpolicymate.policy.catalog;

import kr.youthpolicymate.eligibility.*;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import static kr.youthpolicymate.eligibility.ConditionAssessment.Outcome.*;
import static kr.youthpolicymate.policy.catalog.PolicyQuestions.*;

/** 2026년 5월 신규 모집의 일부 조건을 비교한다. 수집 안내의 충돌과 최종 심사는 별도 확인으로 남긴다. */
public final class YouthTomorrowSavingsRules {
    public static final String NUMBER = "20260430005400113009";
    public static final String CONTENT_HASH = "f3709a60376cdaf861ee232c1fc411292f2d3bdcb28c807ca87190a19698733c";
    public static final String VERSION = "youth-tomorrow-savings-2026-v1";
    public static final String SOURCE = "https://hope.welfareinfo.or.kr/pds/GuideLine.pdf";
    public static final String SCOPE = "2026년 5월 청년내일저축계좌 신규 가입 조건";
    private static final List<Question> QUESTIONS = List.of(
            new Question("birthRange", "2026년 5월 모집의 출생일 범위에 해당하나요?",
                    "1986.5.1.~2011.5.31. 출생자가 대상이에요(양 끝 날짜 포함). 사업 지침은 신청 월에 만 15세 또는 만 40세가 되는 사람까지 포함해요.",
                    List.of(new Option("IN_RANGE", "1986.5.1.~2011.5.31. 출생"), new Option("OUTSIDE_RANGE", "이 출생일 범위에 해당하지 않아요"),
                            new Option("UNKNOWN", "모르겠어요"))),
            new Question("workType", "모집 신청 당시 어떤 소득활동을 했나요?",
                    "자활기업·자활근로사업단 소득은 인정해요. 공공 일자리는 인건비 지원 방식·별도 채용 등 예외를 주민센터에서 확인해주세요.",
                    List.of(new Option("EMPLOYMENT_OR_BUSINESS", "일반 근로·사업"), new Option("SELF_RELIANCE", "자활기업·자활근로"),
                            new Option("PUBLIC_WORK_CONFIRMED", "공공 일자리 · 인정 확인"),
                            new Option("PUBLIC_WORK_PENDING", "공공 일자리 · 확인 중"),
                            new Option("EXCLUDED_ONLY", "근로장학금·실업·육아휴직급여만"), new Option("UNPAID_ONLY", "무급근로만"),
                            new Option("NO_WORK", "근로·사업활동 없음"), new Option("UNKNOWN", "모르겠어요"))),
            new Question("monthlyIncome", "신청 당시 인정되는 본인 월 근로·사업소득은 얼마인가요?",
                    "본인의 세전 근로·사업소득 기준이에요. 근로장학금·실업급여·육아휴직급여는 더하지 마세요. 증빙 인정 여부를 확인 중이면 ‘소득 증빙·금액 확인 중’을 선택해주세요.",
                    List.of(new Option("AT_LEAST_100K", "월 10만 원 이상"), new Option("BELOW_100K", "월 10만 원 미만"),
                            new Option("DOCUMENTS_PENDING", "소득 증빙·금액 확인 중"), new Option("UNKNOWN", "모르겠어요"))),
            new Question("householdIncome", "신청 당시 가구 소득인정액 기준을 확인했나요?",
                    "2026년 가입 기준은 중위소득 50% 이하예요. 소득인정액에는 소득과 재산 환산액이 반영돼요. 가구원 범위와 소득인정액은 주민센터에서 확인해주세요. 월급·건강보험료로 대신 비교할 수 없어요.",
                    List.of(new Option("UP_TO_50_CONFIRMED", "확인 완료 · 50% 이하"),
                            new Option("OVER_50_CONFIRMED", "확인 완료 · 50% 초과"),
                            new Option("ASSESSMENT_PENDING", "가구 범위·소득인정액 확인 중"), new Option("UNKNOWN", "모르겠어요"))),
            new Question("duplicateParticipation", "다른 자산형성사업의 참여 이력을 확인했나요?",
                    "본인·가구원의 현재·과거 참여와 앞으로 참여할 사업을 확인해요. 가구원의 가입이나 지원금 환수 이력만으로 가입이 제한되지는 않으니 사업별로 주민센터에서 확인해주세요.",
                    List.of(new Option("NO_HISTORY", "참여·수혜·예정 없음"), new Option("ALLOWED_CONFIRMED", "참여 이력 · 가입 가능 확인"),
                            new Option("RESTRICTED_CONFIRMED", "중복참여 제한 확인"), new Option("HISTORY_PENDING", "참여·수혜·환수 확인 중"),
                            new Option("UNKNOWN", "모르겠어요"))));

    public static boolean appliesAt(Instant now) { return now.atZone(ZoneId.of("Asia/Seoul")).getYear() == 2026; }

    public static Questionnaire questionnaire(long revision, Instant now) {
        return new Questionnaire(NUMBER, revision, VERSION, true, SCOPE,
                periodNotice(now) + " 연령·근로소득·가구소득·중복참여를 확인해요. 수집 안내와 다른 기준은 2026년 사업 지침을 적용해요.", SOURCE, QUESTIONS);
    }

    public static Evaluation evaluate(long revision, Request input, Instant now) {
        if (!appliesAt(now)) throw new IllegalArgumentException("검토한 2026년 신규 모집 기준만 사용할 수 있습니다.");
        var values = validatedAnswers(QUESTIONS, input.answers());
        var birth = values.get("birthRange");
        var household = values.get("householdIncome");
        var duplicate = values.getOrDefault("duplicateParticipation", "UNKNOWN");
        var checks = List.of(
                birthCheck(birth),
                workIncomeCheck(values),
                check("가구 소득인정액", provided("householdIncome", household), "UP_TO_50_CONFIRMED".equals(household) ? MET : "OVER_50_CONFIRMED".equals(household) ? NOT_MET : UNKNOWN,
                        "신규 가입은 신청 당시 가구 소득인정액이 2026년 기준 중위소득 50% 이하여야 해요. 가입 후 소득 유지 기준과 달라요.",
                        "주민센터에서 가구 범위와 소득·재산을 반영한 소득인정액을 확인해주세요."),
                check("중복참여 제한", provided("duplicateParticipation", values.get("duplicateParticipation")), switch (duplicate) {
                    case "NO_HISTORY", "ALLOWED_CONFIRMED" -> MET;
                    case "RESTRICTED_CONFIRMED" -> NOT_MET;
                    default -> UNKNOWN;
                }, "유사 자산형성사업은 사업 종류·가입자·지원금 수령 또는 환수 여부에 따라 중복참여 기준이 달라요.",
                        "본인과 가구원이 참여한 사업명, 지원금 수령·환수 이력으로 중복참여 제한을 확인해주세요."));
        var evidence = new SourceEvidence(SOURCE, SCOPE, Optional.empty());
        var conditions = new ArrayList<ConditionAssessment>();
        for (int i = 0; i < checks.size(); i++) {
            var check = checks.get(i);
            conditions.add(new ConditionAssessment("youth-tomorrow-savings-" + i, check.label(), Optional.of(check.providedValue()), Optional.empty(), check.outcome(),
                    check.outcome() == UNKNOWN ? Optional.of(ConditionAssessment.Uncertainty.MISSING_USER_INPUT) : Optional.empty(), check.explanation(), evidence));
        }
        var basis = new EvaluationBasis(NUMBER, Long.toString(revision), VERSION, now);
        var common = new EligibilityDecision(basis, PolicyReview.complete(), conditions).status();
        var remaining = List.of("수집 안내에 소득·연령 기준 차이가 있어요. 이 결과는 2026년 신규 모집 공고와 사업 지침의 일부 조건만 비교했어요.",
                "신청서·소득 증빙·가구 조사, 제외업종과 신용정보·계좌 개설 가능 여부는 주민센터에서 확인해주세요.",
                "조건이 맞아도 선정 심사가 남아 있어요. 근로 유지·저축·교육 이수·자금사용계획서 등 가입 후 지급 조건도 별도로 확인해주세요.");
        var whole = new EligibilityDecision(basis, PolicyReview.incomplete(remaining.stream()
                .map(message -> new PolicyReview.PendingIssue(message, evidence)).toList()), conditions);
        return new Evaluation(NUMBER, revision, VERSION, whole.status(), common, SCOPE,
                periodNotice(now) + " 신규 가입 조건만 비교했어요.", remaining, SOURCE, now, checks);
    }

    static Check ageCheck(LocalDate birthDate) {
        return birthCheck(birthDate.isBefore(LocalDate.of(1986, 5, 1)) || birthDate.isAfter(LocalDate.of(2011, 5, 31))
                ? "OUTSIDE_RANGE" : "IN_RANGE");
    }

    private static Check birthCheck(String birth) {
        return check("모집 기준 출생일", provided("birthRange", birth), "IN_RANGE".equals(birth) ? MET : "OUTSIDE_RANGE".equals(birth) ? NOT_MET : UNKNOWN,
                "2026년 5월 모집은 1986.5.1.~2011.5.31. 출생자가 대상이에요. 신청 월에 만 15세 또는 만 40세가 되는 사람을 포함해요.",
                "현재 만 나이가 아닌 2026년 5월 모집의 출생일 범위를 확인해주세요.");
    }

    private static Check workIncomeCheck(Map<String, String> values) {
        var work = values.getOrDefault("workType", "UNKNOWN");
        var amount = values.getOrDefault("monthlyIncome", "UNKNOWN");
        var outcome = switch (work) {
            case "EMPLOYMENT_OR_BUSINESS", "SELF_RELIANCE", "PUBLIC_WORK_CONFIRMED" -> switch (amount) {
                case "AT_LEAST_100K" -> MET;
                case "BELOW_100K" -> NOT_MET;
                default -> UNKNOWN;
            };
            case "EXCLUDED_ONLY", "UNPAID_ONLY", "NO_WORK" -> NOT_MET;
            default -> UNKNOWN;
        };
        return check("본인 근로·사업소득", provided("workType", values.get("workType")) + " · " + provided("monthlyIncome", values.get("monthlyIncome")), outcome,
                "신청 당시 인정되는 근로활동과 본인 세전 근로·사업소득 월 10만 원 이상이 필요해요. 자활근로는 인정하지만 근로장학금·실업급여·육아휴직급여만으로는 가입할 수 없어요.",
                "PUBLIC_WORK_PENDING".equals(work) ? "인건비 지원 방식·별도 채용 등에 따른 소득 인정 여부를 주민센터에서 확인해주세요."
                        : "신청 당시 근로활동과 인정되는 월 소득을 증빙으로 확인해주세요. 확인 중인 소득을 0원으로 처리하지 않아요.");
    }

    static String periodNotice(Instant now) {
        var today = now.atZone(ZoneId.of("Asia/Seoul")).toLocalDate();
        if (today.isAfter(LocalDate.of(2026, 5, 20))) return "2026년 5월 4~20일 모집은 접수가 마감됐어요. 다음 모집 기준은 별도로 확인해주세요.";
        return (today.isBefore(LocalDate.of(2026, 5, 4)) ? "접수 전이에요. " : "") + "이 모집의 접수 기간은 2026년 5월 4~20일이에요.";
    }

    private static String provided(String id, String value) {
        return QUESTIONS.stream().filter(q -> q.id().equals(id)).flatMap(q -> q.options().stream())
                .filter(option -> option.value().equals(value)).map(Option::label).findFirst().orElse("미응답");
    }

    private static Check check(String label, String provided, ConditionAssessment.Outcome outcome, String evidence, String unknownReason) {
        var explanation = outcome == MET ? "입력한 답변은 이 조건을 충족해요."
                : outcome == NOT_MET ? "입력한 답변은 이 조건을 충족하지 않아요." : unknownReason;
        return new Check(label, provided, outcome, explanation, evidence);
    }
}
