package kr.youthpolicymate.policy.catalog;

import kr.youthpolicymate.eligibility.*;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import static kr.youthpolicymate.eligibility.ConditionAssessment.Outcome.*;
import static kr.youthpolicymate.policy.catalog.PolicyQuestions.*;

/** 서울시 공고 2026-1109호의 상반기 기준만 비교한다. 증빙·참여 제한·선발은 별도 확인한다. */
public final class MovingFeeRules {
    public static final String NUMBER = "20260614005400213232";
    public static final String CONTENT_HASH = "e3f828c1c37c1ecddde5a2dc59065e1179642d0fd7be3e919ec8cb9b07441c42";
    public static final String VERSION = "moving-fee-2026-h1-v4";
    public static final String SOURCE = "https://youth.seoul.go.kr/bbs/view.do?key=2303300002&pstSn=2604010002";
    public static final String SCOPE = "2026년 상반기 서울 청년 중개보수·이사비 지원 조건";
    static final Instant OPEN_AT = Instant.parse("2026-04-01T01:00:00Z");
    static final Instant CLOSE_AT = Instant.parse("2026-04-14T09:00:00Z");
    private static final List<Question> QUESTIONS = List.of(
            new Question("birthRange", "1986.1.1.~2007.12.31. 출생인가요?",
                    "공고에 명시된 출생일 범위예요. 양 끝 날짜를 포함하며, 오늘의 만 나이로 계산하지 않아요.",
                    List.of(new Option("IN_RANGE", "해당해요"), new Option("OUTSIDE", "해당하지 않아요"), new Option("UNKNOWN", "모르겠어요"))),
            new Question("move", "이사와 전입신고가 공고의 기간에 해당하나요?",
                    "2024.1.1. 이후 서울로 전입하거나 서울 안에서 이사하고, 2026.4.14. 신청 마감까지 전입신고를 마쳐야 해요. 현재 서울 거주만으로는 확인할 수 없어요.",
                    List.of(new Option("COMPLETED", "기간 내 이사·전입신고를 모두 마쳤어요"), new Option("OUTSIDE", "이사 또는 전입신고가 기간에 맞지 않아요"), new Option("UNKNOWN", "확인 중이에요"))),
            new Question("contract", "신청 당시 계약·세대주·주민등록 조건을 모두 갖췄나요?",
                    "본인이 세대주이자 임대차계약의 임차인이며, 계약한 집에 주민등록이 있어야 해요. 부모·배우자 등 동거인이 있어도 가능해요.",
                    List.of(new Option("ALL", "세 조건을 모두 갖췄어요"), new Option("MISSING", "갖추지 못한 조건이 있어요"), new Option("UNKNOWN", "확인 중이에요"))),
            new Question("homeOwnership", "신청 당시 본인 명의 주택이나 입주권이 있었나요?",
                    "분양권·조합원 입주권·공유지분도 포함해요. 전세사기 피해 주택을 경·공매로 취득한 경우 등은 예외를 확인해주세요. 청약의 무주택 기준과 달라요.",
                    List.of(new Option("NO_HOME", "모두 없었어요"), new Option("OWNS_NO_EXCEPTION", "있었고 예외에도 해당하지 않아요"),
                            new Option("EXCEPTION_PENDING", "전세사기 피해 등 예외 확인이 필요해요"), new Option("UNKNOWN", "확인 중이에요"))),
            new Question("housingCost", "공고 기준 주택 거래금액이 2억 원 이하인가요?",
                    "거래금액은 보증금 + 월세 × 100이에요. 전·월세 거주 여부도 확인해주세요. 월 소득이나 주택 매매가를 입력하는 항목이 아니에요.",
                    List.of(new Option("WITHIN_LIMIT", "전·월세이며 거래금액이 2억 원 이하예요"), new Option("OUTSIDE", "전·월세가 아니거나 2억 원을 넘어요"), new Option("UNKNOWN", "확인 중이에요"))),
            new Question("income", "2026년 3월 건강보험료가 공고 기준 이하인가요?",
                    "공고 2쪽의 가구원 수·가입 유형별 중위소득 150% 기준표와 비교해주세요. 장기요양보험료는 빼고, 피부양자는 주소가 달라도 부양자 고지금액을 사용해요. 월급으로 대신 비교하지 않아요.",
                    List.of(new Option("WITHIN_LIMIT", "공고 기준표로 확인 · 기준 이하"), new Option("ABOVE_LIMIT", "공고 기준표로 확인 · 기준 초과"),
                            new Option("DEPENDENT_PENDING", "부양자 보험료 확인 중"), new Option("HOUSEHOLD_PENDING", "가구원 수·가입 유형 확인 중"),
                            new Option("DOCUMENTS_PENDING", "보험료 조회 불가 · 대체 증빙 확인 중"), new Option("UNKNOWN", "모르겠어요"))),
            new Question("seoulSupport", "이 공고 신청 전에 서울시 이 사업의 지원을 받은 적 있나요?",
                    "서울시 청년 중개보수·이사비 지원은 생애 1회예요. 자치구·LH·SH 등 다른 기관의 지원은 다음 질문에서 답해주세요.",
                    List.of(new Option("NONE", "받은 적 없어요"), new Option("RECEIVED", "서울시 이 사업에서 지원받았어요"), new Option("UNKNOWN", "사업명·수혜 이력 확인 중"))),
            new Question("otherSupport", "다른 기관에서 어떤 비용을 지원받았나요?",
                    "신청 전까지 2022.1.1. 이후 서울 전입·서울 내 이사로 받은 지원을 확인해주세요. 자치구·중앙부처·LH·SH 등을 포함해요. 생필품비 등 지원 항목이 불명확하면 기관에서 확인해주세요.",
                    List.of(new Option("NONE", "받은 적 없어요"), new Option("BROKERAGE_ONLY", "중개보수만 받았어요"), new Option("MOVING_ONLY", "이사비만 받았어요"),
                            new Option("BOTH", "중개보수·이사비 모두 받았어요"), new Option("UNKNOWN", "지원 기관·항목 확인 중"))),
            new Question("requestedCost", "이 공고 기준으로 확인할 비용은 무엇인가요?",
                    "중개보수·이사비 중 한 가지만 신청할 수도 있어요. 다른 기관에서 한 종류 비용을 받았다면 다른 비용만 확인해주세요.",
                    List.of(new Option("BROKERAGE", "중개보수만"), new Option("MOVING", "이사비만"), new Option("BOTH", "중개보수·이사비 모두"), new Option("UNKNOWN", "아직 정하지 않았어요"))),
            new Question("parentRental", "신청 당시 임차한 집이 부모님 소유였나요?",
                    "부모 소유 주택을 임차하면 참여 대상에서 제외돼요. 부모와 함께 살았는지가 아니라 임차주택의 소유자를 확인해주세요.",
                    List.of(new Option("CLEAR", "부모 소유 주택이 아니었어요"), new Option("RESTRICTED", "부모 소유 주택이었어요"), new Option("UNKNOWN", "소유 관계 확인 중"))),
            new Question("benefitReceipt", "신청 당시 생계·의료·주거급여를 받고 있었나요?",
                    "세 급여 중 하나라도 받고 있었다면 참여 대상에서 제외돼요. 교육급여만 받는 경우는 이 수급 제한에 포함하지 않아요.",
                    List.of(new Option("CLEAR", "세 급여 모두 받지 않았어요"), new Option("RESTRICTED", "한 가지 이상 받고 있었어요"), new Option("UNKNOWN", "수급 종류·시점 확인 중"))),
            new Question("excludedResidency", "신청 당시 외국인·재외국민에 해당했나요?",
                    "공고는 외국인·재외국민을 지원 대상에서 제외해요. 해당 여부가 불분명하면 담당 기관에서 확인해주세요. 국적명이나 증빙서류는 입력하지 않아요.",
                    List.of(new Option("CLEAR", "둘 다 해당하지 않았어요"), new Option("RESTRICTED", "외국인 또는 재외국민이었어요"), new Option("UNKNOWN", "해당 여부 확인 중"))));

    public static boolean appliesAt(Instant now) { return now.atZone(ZoneId.of("Asia/Seoul")).getYear() == 2026; }
    public static Questionnaire questionnaire(long revision, Instant now) {
        return new Questionnaire(NUMBER, revision, VERSION, true, SCOPE,
                periodNotice(now) + " 연령·이사·계약·주택·소득·중복지원과 공고의 참여 제한을 확인해요. 증빙과 선발 심사 등은 별도 확인이 필요해요.", SOURCE, QUESTIONS);
    }
    static Check ageCheck(LocalDate birthDate) {
        return birthCheck(birthDate.isBefore(LocalDate.of(1986, 1, 1)) || birthDate.isAfter(LocalDate.of(2007, 12, 31)) ? "OUTSIDE" : "IN_RANGE");
    }
    private static Check birthCheck(String value) {
        return check("birthRange", "공고의 출생일 기준", value, "IN_RANGE", "OUTSIDE",
                "2026년 상반기 공고는 1986.1.1.~2007.12.31. 출생자를 대상으로 해요(양 끝 날짜 포함).");
    }
    public static Evaluation evaluate(long revision, Request input, Instant now) {
        if (!appliesAt(now)) throw new IllegalArgumentException("검토한 2026년 상반기 모집 기준만 사용할 수 있습니다.");
        var values = validatedAnswers(QUESTIONS, input.answers());
        var checks = List.of(birthCheck(values.get("birthRange")),
                check("move", "이사·전입신고 기간", values.get("move"), "COMPLETED", "OUTSIDE", "2024.1.1. 이후 서울 전입 또는 서울 내 이사와 2026.4.14. 마감까지 전입신고가 필요해요."),
                check("contract", "계약·세대주·주민등록", values.get("contract"), "ALL", "MISSING", "신청자 본인이 세대주·임차인이며 임차주택에 주민등록이 있어야 해요. 동거인은 허용돼요."),
                check("homeOwnership", "본인 주택 소유", values.get("homeOwnership"), "NO_HOME", "OWNS_NO_EXCEPTION", "본인 무주택이 원칙이며 분양권·입주권·공유지분도 포함해요. 전세사기 피해 주택 취득 등 예외는 기관 확인이 필요해요."),
                check("housingCost", "주택 거래금액", values.get("housingCost"), "WITHIN_LIMIT", "OUTSIDE", "전·월세 주택의 보증금 + 월세 × 100이 2억 원 이하여야 해요."),
                check("income", "건강보험료 기준 소득", values.get("income"), "WITHIN_LIMIT", "ABOVE_LIMIT",
                        "2026년 3월 건강보험료 고지금액(장기요양보험료 제외)이 공고 2쪽의 가구원 수·가입 유형별 중위소득 150% 기준 이하여야 해요. 피부양자는 주소가 분리돼도 부양자의 고지금액으로 비교해요. 조회가 어려우면 기관에서 대체 소득 증빙을 확인해야 해요."),
                duplicateSupportCheck(values.get("seoulSupport"), values.get("otherSupport"), values.get("requestedCost")),
                check("parentRental", "부모 소유 주택 임차 제한", values.get("parentRental"), "CLEAR", "RESTRICTED",
                        "공고 3쪽은 부모 소유 주택을 임차하는 경우를 참여 제한 대상으로 정해요. 부모와의 동거 여부와 구분해요."),
                check("benefitReceipt", "생계·의료·주거급여 수급 제한", values.get("benefitReceipt"), "CLEAR", "RESTRICTED",
                        "공고 3쪽은 생계·의료·주거급여 수급자를 참여 제한 대상으로 정해요. 교육급여만 받는 경우는 이 세 급여 수급에 포함하지 않아요."),
                check("excludedResidency", "외국인·재외국민 제한", values.get("excludedResidency"), "CLEAR", "RESTRICTED",
                        "공고 2쪽은 외국인·재외국민을 지원 대상에서 제외해요. 신청 당시 해당 여부를 확인해요."));
        var evidence = new SourceEvidence(SOURCE, SCOPE, Optional.empty());
        var conditions = IntStream.range(0, checks.size()).mapToObj(i -> {
            var check = checks.get(i);
            return new ConditionAssessment("moving-fee-" + i, check.label(), Optional.of(check.providedValue()), Optional.empty(), check.outcome(),
                    check.outcome() == UNKNOWN ? Optional.of(ConditionAssessment.Uncertainty.MISSING_USER_INPUT) : Optional.empty(), check.explanation(), evidence);
        }).toList();
        var basis = new EvaluationBasis(NUMBER, Long.toString(revision), VERSION, now);
        var remaining = List.of(
                "보험료·가구원 수·가입 유형은 입력한 답변으로 비교했어요. 증빙 인정 여부와 소득 심사는 담당 기관에서 확인해주세요.",
                "지원 기관·항목과 수혜 이력의 증빙을 확인해주세요. 다른 비용의 중복지원 조건이 충족돼도 실제 인정 비용과 지급액은 별도 심사예요.",
                costNotice(values.get("requestedCost")),
                "참여 제한은 입력한 답변으로 확인했어요. 증빙 적격 여부와 기타 사업 취지에 따른 제한은 담당 기관에서 확인해주세요.",
                "생애 1회·최대 40만 원 실비 지원이며 우선선발·소득 순 심사를 거쳐요. 실제 선정과 지급은 공식 결과를 확인해주세요.");
        var review = PolicyReview.incomplete(remaining.stream().map(message -> new PolicyReview.PendingIssue(message, evidence)).toList());
        return new Evaluation(NUMBER, revision, VERSION, new EligibilityDecision(basis, review, conditions).status(),
                new EligibilityDecision(basis, PolicyReview.complete(), conditions).status(), SCOPE,
                periodNotice(now) + " 입력한 조건의 비교 결과이며, 증빙·기타 참여 제한·선발 심사는 별도예요.", remaining, SOURCE, now, checks);
    }
    private static String costNotice(String requested) {
        var period = "2024.1.1.~2026.4.14. 지출을 마친 비용과 증빙을 확인해주세요. ";
        var brokerage = "중개보수는 임대차계약 체결 비용을 확인하며, 재계약·중도 퇴실로 발생한 비용은 제외돼요.";
        var moving = "이사비는 개인용달·(반)포장이사·사다리차 이용비 등을 확인해요. 청소·택배·대중교통·택시·렌터카 비용은 제외돼요.";
        return period + switch (requested == null ? "" : requested) {
            case "BROKERAGE" -> brokerage;
            case "MOVING" -> moving;
            case "BOTH" -> brokerage + " " + moving;
            default -> "확인할 비용을 선택하면 중개보수·이사비의 인정 범위와 제외 항목을 안내해요.";
        };
    }
    private static Check duplicateSupportCheck(String seoul, String other, String requested) {
        ConditionAssessment.Outcome outcome;
        String explanation;
        if ("RECEIVED".equals(seoul)) {
            outcome = NOT_MET;
            explanation = "서울시 이 사업은 생애 1회 지원해요. 타 기관의 한쪽 비용 지원 예외를 서울시 재수혜에 적용하지 않아요.";
        } else if ("BOTH".equals(other)) {
            outcome = NOT_MET;
            explanation = "타 기관에서 두 비용을 모두 지원받아 중복지원 조건을 충족하지 않아요.";
        } else if (!"NONE".equals(seoul) || other == null || "UNKNOWN".equals(other)) {
            outcome = UNKNOWN;
            explanation = "서울시 사업 수혜 여부와 타 기관의 지원 항목을 확인해주세요. 기관이나 항목이 불명확하면 지원 이력 없음으로 판단하지 않아요.";
        } else if ("NONE".equals(other)) {
            outcome = MET;
            explanation = "입력한 답변에는 이전 지원 이력이 없어요. 신청 비용의 인정 여부는 별도 확인이 필요해요.";
        } else if (requested == null || "UNKNOWN".equals(requested)) {
            outcome = UNKNOWN;
            explanation = "이미 지원받은 비용과 비교할 신청 비용을 선택해주세요.";
        } else {
            var receivedLabel = "BROKERAGE_ONLY".equals(other) ? "중개보수" : "이사비";
            var remainingLabel = "BROKERAGE_ONLY".equals(other) ? "이사비" : "중개보수";
            var separateCost = "BROKERAGE_ONLY".equals(other) ? "MOVING" : "BROKERAGE";
            if ("BOTH".equals(requested)) {
                outcome = UNKNOWN;
                explanation = "이미 지원받은 " + receivedLabel + "는 중복돼요. " + remainingLabel + "만 선택해 다시 확인해주세요.";
            } else if (separateCost.equals(requested)) {
                outcome = MET;
                explanation = "타 기관에서 " + receivedLabel + "만 받았다면 " + remainingLabel + "의 중복지원 조건은 충족해요. 실제 비용과 증빙은 별도 심사해요.";
            } else {
                outcome = NOT_MET;
                explanation = "이미 지원받은 " + receivedLabel + "를 다시 신청하는 경우예요. " + remainingLabel + "만 선택하면 별도로 확인할 수 있어요.";
            }
        }
        return new Check("중복지원 제한", "서울시 사업: " + providedValue("seoulSupport", seoul)
                + " / 타 기관: " + providedValue("otherSupport", other) + " / 확인할 비용: " + providedValue("requestedCost", requested),
                outcome, explanation, "서울시 이 사업은 생애 1회예요. 공고는 2022.1.1. 이후 서울 전입·서울 내 이사에 대한 서울시·타 기관 지원 이력을 확인해요. 타 기관에서 한 종류 비용만 받았다면 다른 비용에 한해 지원을 확인할 수 있어요. 근거: 공고 1·3쪽.");
    }
    static String periodNotice(Instant now) {
        if (!now.isBefore(CLOSE_AT)) return "2026년 상반기 접수는 4월 14일 18:00(서울)에 마감됐어요.";
        var period = "접수 기간은 2026.4.1. 10:00~4.14. 18:00(서울)이에요.";
        return now.isBefore(OPEN_AT) ? "접수 전이에요. " + period : period;
    }
    private static Check check(String id, String label, String value, String met, String notMet, String evidence) {
        var outcome = met.equals(value) ? MET : notMet.equals(value) ? NOT_MET : UNKNOWN;
        return new Check(label, providedValue(id, value), outcome, outcome == MET ? "입력한 답변은 이 조건을 충족해요."
                : outcome == NOT_MET ? "입력한 답변은 이 조건을 충족하지 않아요."
                : unknownReason(value), evidence);
    }
    private static String providedValue(String id, String value) {
        return QUESTIONS.stream().filter(q -> q.id().equals(id)).flatMap(q -> q.options().stream())
                .filter(option -> option.value().equals(value)).map(Option::label).findFirst().orElse("미응답");
    }
    private static String unknownReason(String value) {
        return switch (value == null ? "" : value) {
            case "EXCEPTION_PENDING" -> "피해 주택 취득에 따른 예외를 담당 기관에서 확인해주세요.";
            case "DEPENDENT_PENDING" -> "피부양자는 본인 보험료가 0원이어도 충족으로 판단하지 않아요. 부양자의 2026년 3월 고지금액을 확인해주세요.";
            case "HOUSEHOLD_PENDING" -> "공고 기준 가구원 수와 가입 유형을 확인한 뒤 해당 보험료 기준과 비교해주세요.";
            case "DOCUMENTS_PENDING" -> "보험료를 조회할 수 없다는 이유로 불충족 처리하지 않아요. 담당 기관에서 대체 소득 증빙을 확인해주세요.";
            default -> "이 항목을 확인한 뒤 다시 답해주세요.";
        };
    }
}
