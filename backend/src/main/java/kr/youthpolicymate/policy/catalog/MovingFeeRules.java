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

/** 서울시 공고 2026-1109호의 상반기 기준만 비교한다. 소득·중복 지원·선발은 별도 확인한다. */
public final class MovingFeeRules {
    public static final String NUMBER = "20260614005400213232";
    public static final String CONTENT_HASH = "e3f828c1c37c1ecddde5a2dc59065e1179642d0fd7be3e919ec8cb9b07441c42";
    public static final String VERSION = "moving-fee-2026-h1-v1";
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
                    List.of(new Option("WITHIN_LIMIT", "전·월세이며 거래금액이 2억 원 이하예요"), new Option("OUTSIDE", "전·월세가 아니거나 2억 원을 넘어요"), new Option("UNKNOWN", "확인 중이에요"))));

    public static boolean appliesAt(Instant now) { return now.atZone(ZoneId.of("Asia/Seoul")).getYear() == 2026; }
    public static Questionnaire questionnaire(long revision, Instant now) {
        return new Questionnaire(NUMBER, revision, VERSION, true, SCOPE,
                periodNotice(now) + " 연령·이사·계약·주택 조건을 확인해요. 소득과 중복지원 등은 별도 확인이 필요해요.", SOURCE, QUESTIONS);
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
                check("housingCost", "주택 거래금액", values.get("housingCost"), "WITHIN_LIMIT", "OUTSIDE", "전·월세 주택의 보증금 + 월세 × 100이 2억 원 이하여야 해요."));
        var evidence = new SourceEvidence(SOURCE, SCOPE, Optional.empty());
        var conditions = IntStream.range(0, checks.size()).mapToObj(i -> {
            var check = checks.get(i);
            return new ConditionAssessment("moving-fee-" + i, check.label(), Optional.of(check.providedValue()), Optional.empty(), check.outcome(),
                    check.outcome() == UNKNOWN ? Optional.of(ConditionAssessment.Uncertainty.MISSING_USER_INPUT) : Optional.empty(), check.explanation(), evidence);
        }).toList();
        var basis = new EvaluationBasis(NUMBER, Long.toString(revision), VERSION, now);
        var remaining = List.of(
                "2026년 3월 건강보험료·가구원 기준으로 중위소득 150% 이하인지 확인해주세요. 피부양자는 부양자의 보험료를 사용해요.",
                "과거 서울시·타 기관 지원 이력을 확인해주세요. 다른 기관에서 중개보수 또는 이사비 한 종류만 받았다면 다른 비용은 지원 가능한 예외가 있어요.",
                "부모 소유 주택 임차, 생계·의료·주거급여 수급, 외국인·재외국민 여부 등 참여 제한을 확인해주세요.",
                "2024.1.1.~2026.4.14. 지출한 인정 비용과 증빙을 확인해주세요. 재계약·중도 퇴실 중개보수나 청소·택배비 등은 지원하지 않아요.",
                "생애 1회·최대 40만 원 실비 지원이며 우선선발·소득 순 심사를 거쳐요. 실제 선정과 지급은 공식 결과를 확인해주세요.");
        var review = PolicyReview.incomplete(remaining.stream().map(message -> new PolicyReview.PendingIssue(message, evidence)).toList());
        return new Evaluation(NUMBER, revision, VERSION, new EligibilityDecision(basis, review, conditions).status(),
                new EligibilityDecision(basis, PolicyReview.complete(), conditions).status(), SCOPE,
                periodNotice(now) + " 확인한 다섯 조건의 결과이며, 소득·참여 제한·증빙 심사는 별도예요.", remaining, SOURCE, now, checks);
    }
    static String periodNotice(Instant now) {
        if (!now.isBefore(CLOSE_AT)) return "2026년 상반기 접수는 4월 14일 18:00(서울)에 마감됐어요.";
        var period = "접수 기간은 2026.4.1. 10:00~4.14. 18:00(서울)이에요.";
        return now.isBefore(OPEN_AT) ? "접수 전이에요. " + period : period;
    }
    private static Check check(String id, String label, String value, String met, String notMet, String evidence) {
        var outcome = met.equals(value) ? MET : notMet.equals(value) ? NOT_MET : UNKNOWN;
        var provided = QUESTIONS.stream().filter(q -> q.id().equals(id)).flatMap(q -> q.options().stream())
                .filter(option -> option.value().equals(value)).map(Option::label).findFirst().orElse("미응답");
        return new Check(label, provided, outcome, outcome == MET ? "입력한 답변은 이 조건을 충족해요."
                : outcome == NOT_MET ? "입력한 답변은 이 조건을 충족하지 않아요."
                : "EXCEPTION_PENDING".equals(value) ? "피해 주택 취득에 따른 예외를 담당 기관에서 확인해주세요." : "이 항목을 확인한 뒤 다시 답해주세요.", evidence);
    }
}
