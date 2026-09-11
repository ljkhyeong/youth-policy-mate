package kr.youthpolicymate.policy.catalog;

import kr.youthpolicymate.eligibility.*;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import static kr.youthpolicymate.eligibility.ConditionAssessment.Outcome.*;
import static kr.youthpolicymate.policy.catalog.PolicyQuestions.*;

/** 이전 결과 비교용 고정 자료. 서울시 공고 제2026-1444호의 5월 모집만 비교한다. 이후 모집과 최종 선발은 별도 확인한다. */
public final class FutureYouthJobsRules {
    public static final String NUMBER = "20260722005400213264";
    public static final String CONTENT_HASH = "0d98b50fc87fc4e319676be23e6a900304a4434215e997004ed3e1f47bb8dfea";
    public static final String VERSION = "future-youth-jobs-2026-may-v1";
    public static final String SOURCE = "https://youth.seoul.go.kr/bbs/view.do?key=2303300002&pstSn=2605040004";
    public static final String SCOPE = "2026년 5월 미래 청년 일자리 참여 조건";
    private static final Instant ANNOUNCED_AT = Instant.parse("2026-05-03T15:00:00Z");
    private static final Instant OPEN_AT = Instant.parse("2026-05-17T15:00:00Z");
    private static final Instant CLOSED_AT = Instant.parse("2026-05-31T15:00:00Z");
    private static final List<Question> QUESTIONS = List.of(
            new Question("birthRange", "공고의 출생일 범위에 해당하나요?",
                    "1986.1.1.~2007.12.31. 출생자가 기본 대상이에요. 의무복무 제대군인은 복무기간 1년 미만은 1985년생, 1~2년 미만은 1984년생, 2년 이상은 1983년생까지 연장해요. 적용 여부는 운영사무국에서 확인해주세요.",
                    List.of(new Option("BASE_RANGE", "1986.1.1.~2007.12.31. 출생"),
                            new Option("EXTENSION_CONFIRMED", "기관 확인 · 연장된 연령 기준 충족"),
                            new Option("TOO_YOUNG", "2008.1.1. 이후 출생"),
                            new Option("OLDER_NOT_ELIGIBLE", "1985.12.31.까지 출생 · 연장 불가 또는 연장 상한 초과"),
                            new Option("EXTENSION_PENDING", "군복무에 따른 연령 연장 확인 중"), new Option("UNKNOWN", "모르겠어요"))),
            new Question("residence", "이 모집에 신청할 당시 서울에 주민등록이 되어 있었나요?",
                    "서울 소재 대학·직장만으로 거주 조건을 충족하지는 않아요. 등록 형태나 증빙 인정 여부가 불분명하면 운영사무국에서 확인해주세요.",
                    List.of(new Option("SEOUL", "서울 주민등록 확인"), new Option("OUTSIDE", "서울 외 지역 주민등록 확인"),
                            new Option("UNKNOWN", "등록·증빙 확인 중"))),
            new Question("employment", "신청서 제출일의 근로 상태는 무엇인가요?",
                    "근로 중이어도 주 30시간 이하 또는 근로계약기간 3개월 미만이면 참여할 수 있어요. 사업자등록은 아래에서 별도로 확인해요.",
                    List.of(new Option("NOT_WORKING", "근로 중이 아니에요"), new Option("UP_TO_30_HOURS", "주 30시간 이하 근로"),
                            new Option("UNDER_3_MONTHS", "근로계약기간 3개월 미만"),
                            new Option("OVER_LIMITS", "주 30시간 초과 · 계약기간 3개월 이상"), new Option("UNKNOWN", "근로시간·계약기간 확인 중"))),
            new Question("education", "공고 기준의 대학·대학원 재학 상태는 무엇인가요?",
                    "수료·졸업예정·졸업유예와 방송통신·사이버·야간대학(원) 재학생은 예외예요. 졸업예정은 공고일 기준 마지막 학년 2학기 재학 또는 이수 여부를 확인해주세요. 예외 증빙은 학교·운영사무국에서 확인해요.",
                    List.of(new Option("NOT_ENROLLED", "재학·휴학 중이 아니에요"), new Option("EXCEPTION_CONFIRMED", "재학 예외 · 증빙 인정 확인"),
                            new Option("EXCLUDED_CONFIRMED", "재학·휴학 중 · 예외 해당 없음"), new Option("UNKNOWN", "재학 예외 확인 중"))),
            new Question("business", "신청 당시 사업자등록이 있었나요?",
                    "등록이 있어도 휴업 등 실제 미영업을 증명하거나, 근로자·임대사무실이 없는 부동산임대업임을 증명하면 참여할 수 있어요. 증빙 인정 여부는 운영사무국에서 확인해주세요.",
                    List.of(new Option("NONE", "사업자등록 없음"), new Option("INACTIVE_CONFIRMED", "미영업 증빙 인정 확인"),
                            new Option("RENTAL_EXCEPTION_CONFIRMED", "부동산임대업 예외 인정 확인"),
                            new Option("ACTIVE_NO_EXCEPTION", "사업자등록 있음 · 예외 해당 없음"), new Option("UNKNOWN", "사업자등록·예외 확인 중"))),
            new Question("publicJob", "다른 정부·서울시 일자리 사업에 참여 중이었나요?",
                    "서울시 매력일자리·공공근로·지역주도형 청년일자리 등이 해당해요. 교육이나 수당만 받는 경우는 일자리 사업 참여에 해당하는지 운영사무국에서 확인해주세요.",
                    List.of(new Option("NO", "참여하지 않았어요"), new Option("YES", "참여 중이었어요"), new Option("UNKNOWN", "사업 유형·참여 여부 확인 중"))));

    public static boolean appliesAt(Instant now) {
        return !now.isBefore(ANNOUNCED_AT) && now.atZone(ZoneId.of("Asia/Seoul")).getYear() == 2026;
    }

    public static Questionnaire questionnaire(long revision, Instant now) {
        return new Questionnaire(NUMBER, revision, VERSION, true, SCOPE,
                periodNotice(now) + " 5월 모집 당시의 조건을 답해주세요. 이후 2차 모집에는 적용하지 않아요.", SOURCE, QUESTIONS);
    }

    public static Evaluation evaluate(long revision, Request input, Instant now) {
        if (!appliesAt(now)) throw new IllegalArgumentException("검토한 2026년 5월 모집 기준만 사용할 수 있습니다.");
        var values = validatedAnswers(QUESTIONS, input.answers());
        var checks = List.of(
                birthCheck(values.get("birthRange")),
                check("residence", "서울 주민등록", values.get("residence"),
                        switch (values.getOrDefault("residence", "UNKNOWN")) { case "SEOUL" -> MET; case "OUTSIDE" -> NOT_MET; default -> UNKNOWN; },
                        "주민등록 기준 서울 거주자를 모집해요. 등록 형태와 증빙 인정 여부를 확인해주세요."),
                check("employment", "근로 상태", values.get("employment"),
                        switch (values.getOrDefault("employment", "UNKNOWN")) { case "NOT_WORKING", "UP_TO_30_HOURS", "UNDER_3_MONTHS" -> MET; case "OVER_LIMITS" -> NOT_MET; default -> UNKNOWN; },
                        "신청서 제출일 기준 미취업이거나, 주 30시간 이하 또는 계약기간 3개월 미만 근로 예외에 해당해야 해요."),
                check("education", "재학·휴학 제한", values.get("education"),
                        switch (values.getOrDefault("education", "UNKNOWN")) { case "NOT_ENROLLED", "EXCEPTION_CONFIRMED" -> MET; case "EXCLUDED_CONFIRMED" -> NOT_MET; default -> UNKNOWN; },
                        "대학·대학원 재학·휴학은 원칙적으로 제외하지만 수료·졸업예정·졸업유예·방송통신·사이버·야간대학(원) 재학 예외가 있어요. 증빙 인정 여부를 확인해주세요."),
                check("business", "사업자등록 제한", values.get("business"),
                        switch (values.getOrDefault("business", "UNKNOWN")) { case "NONE", "INACTIVE_CONFIRMED", "RENTAL_EXCEPTION_CONFIRMED" -> MET; case "ACTIVE_NO_EXCEPTION" -> NOT_MET; default -> UNKNOWN; },
                        "사업자등록이 있으면 실제 미영업이나 근로자·임대사무실 없는 부동산임대업의 증빙 예외를 확인해야 해요."),
                check("publicJob", "다른 일자리 사업 참여", values.get("publicJob"),
                        switch (values.getOrDefault("publicJob", "UNKNOWN")) { case "NO" -> MET; case "YES" -> NOT_MET; default -> UNKNOWN; },
                        "정부·서울시 일자리 창출 사업 참여자는 제외해요. 해당 사업의 유형과 참여 여부를 확인해주세요."));
        var evidence = new SourceEvidence(SOURCE, SCOPE, Optional.empty());
        var conditions = new ArrayList<ConditionAssessment>();
        for (int i = 0; i < checks.size(); i++) {
            var check = checks.get(i);
            conditions.add(new ConditionAssessment("future-youth-jobs-" + i, check.label(), Optional.of(check.providedValue()), Optional.empty(), check.outcome(),
                    check.outcome() == UNKNOWN ? Optional.of(ConditionAssessment.Uncertainty.MISSING_USER_INPUT) : Optional.empty(), check.explanation(), evidence));
        }
        var remaining = List.of("등록·근로계약·재학·사업 예외의 증빙 인정 여부를 운영사무국에서 확인해주세요.",
                "직무별 법정 제한과 근로 가능 여부는 담당 기관에서 확인해요. 민감한 이력이나 증빙은 이곳에 입력하지 않아요.",
                "신청서·동의서·가점 증빙, 필수 교육 참여와 서류·면접 심사는 별도 확인이 필요해요. 실제 선발은 공식 결과를 확인해주세요.",
                "기초생활수급자는 근로소득 발생으로 수급자 지위가 달라질 수 있어요. 참여 전 주민센터에서 상담해주세요.");
        var basis = new EvaluationBasis(NUMBER, Long.toString(revision), VERSION, now);
        var common = new EligibilityDecision(basis, PolicyReview.complete(), conditions).status();
        var whole = new EligibilityDecision(basis, PolicyReview.incomplete(remaining.stream()
                .map(message -> new PolicyReview.PendingIssue(message, evidence)).toList()), conditions);
        return new Evaluation(NUMBER, revision, VERSION, whole.status(), common, SCOPE,
                periodNotice(now) + " 5월 모집의 여섯 조건을 비교한 결과예요. 이후 모집 기준은 별도 확인해주세요.", remaining, SOURCE, now, checks);
    }

    static Check ageCheck(LocalDate birth) {
        return birthCheck(birth.isBefore(LocalDate.of(1986, 1, 1)) ? "EXTENSION_PENDING"
                : birth.isAfter(LocalDate.of(2007, 12, 31)) ? "TOO_YOUNG" : "BASE_RANGE");
    }

    private static Check birthCheck(String value) {
        return check("birthRange", "공고의 연령 기준", value,
                switch (value == null ? "" : value) { case "BASE_RANGE", "EXTENSION_CONFIRMED" -> MET; case "TOO_YOUNG", "OLDER_NOT_ELIGIBLE" -> NOT_MET; default -> UNKNOWN; },
                "1986.1.1.~2007.12.31. 출생자가 기본 대상이에요. 의무복무 제대군인은 복무기간에 따라 1985·1984·1983년생까지 연장하므로 적용 여부를 확인해주세요.");
    }

    static String periodNotice(Instant now) {
        if (!now.isBefore(CLOSED_AT)) return "2026년 5월 모집은 5월 31일 23:59(서울)에 접수가 마감됐어요.";
        var period = "신청 기간은 2026년 5월 18일~5월 31일 23:59(서울)이에요. 5월 4일은 공고 시작일이에요.";
        return now.isBefore(OPEN_AT) ? "접수 전이에요. " + period : period;
    }

    private static Check check(String id, String label, String value, ConditionAssessment.Outcome outcome, String evidence) {
        var provided = QUESTIONS.stream().filter(question -> question.id().equals(id)).flatMap(question -> question.options().stream())
                .filter(option -> option.value().equals(value)).map(Option::label).findFirst().orElse("미응답");
        var explanation = outcome == MET ? "입력한 답변은 이 조건을 충족해요."
                : outcome == NOT_MET ? "입력한 답변은 이 조건을 충족하지 않아요." : evidence;
        return new Check(label, provided, outcome, explanation, evidence);
    }
}
