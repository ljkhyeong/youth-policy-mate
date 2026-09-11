package kr.youthpolicymate.policy.catalog;

import kr.youthpolicymate.eligibility.*;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import static kr.youthpolicymate.eligibility.ConditionAssessment.Outcome.*;
import static kr.youthpolicymate.policy.catalog.PolicyQuestions.*;

/** 2026년 하반기 모집의 연령·서울 거주 또는 생활권·위원 이력만 비교한다. 최종 선발은 별도 심사다. */
public final class SeoulYouthNetworkRules {
    public static final String NUMBER = "20260520005400213208";
    public static final String CONTENT_HASH = "f5ae512cf9721607bb849c8d466db4b21e17eb2c8b84eb8dda013fa158d98ca7";
    public static final String VERSION = "seoul-network-2026-h2-v1";
    public static final String SOURCE = "https://youth.seoul.go.kr/bbs/view.do?key=2303300002&pstSn=2605150002";
    public static final String SCOPE = "2026년 하반기 서울청년정책네트워크 참여 조건";
    static final Instant OPEN_AT = Instant.parse("2026-05-20T00:00:00Z");
    static final Instant CLOSE_AT = Instant.parse("2026-05-29T08:00:00Z");
    private static final List<Question> QUESTIONS = List.of(
            new Question("birthRange", "공고의 출생일 범위에 해당하나요?",
                    "기본 대상은 1986.1.2.~2007.1.1. 출생자예요(양 끝 날짜 포함). 의무복무 제대군인은 최대 3세까지 연령 상한을 연장해요. 연장 후 기준 충족 여부는 담당 기관에서 확인해주세요.",
                    List.of(new Option("BASE_RANGE", "1986.1.2.~2007.1.1. 출생"),
                            new Option("MILITARY_EXTENSION_CONFIRMED", "기관 확인 · 연장된 연령 기준 충족"),
                            new Option("TOO_YOUNG", "2007.1.2. 이후 출생"),
                            new Option("OLDER_NO_EXTENSION", "1986.1.1.까지 출생 · 연장 불가"),
                            new Option("EXTENDED_LIMIT_EXCEEDED", "연장 후에도 연령 상한 초과"),
                            new Option("MILITARY_EXTENSION_PENDING", "군복무에 따른 연령 연장 확인 중"),
                            new Option("UNKNOWN", "모르겠어요"))),
            new Question("seoulConnection", "신청 당시 서울 거주·재학·재직 중이었나요?",
                    "서울 거주·대학·직장 중 하나에 해당하면 돼요. 대학 재·휴학과 직장 재·휴직을 포함해요. 사업자는 서울 생활권 증빙을 담당 기관에서 확인해주세요.",
                    List.of(new Option("RESIDENT", "서울 거주"), new Option("UNIVERSITY", "서울 소재 대학 재학·휴학"),
                            new Option("WORKPLACE", "서울 소재 직장 재직·휴직"),
                            new Option("BUSINESS_CONFIRMED", "서울 사업장 · 기관에서 증빙 인정"),
                            new Option("NONE_CONFIRMED", "모두 해당 없음 · 기관 확인"),
                            new Option("UNKNOWN", "모르겠어요"))),
            new Question("consecutiveTerms", "2023~2025년 청정넷 위원으로 연속 활동했나요?",
                    "공고는 2023~2025년 2차례 연임자를 제외해요. 중간 공백 등으로 연임 여부가 불분명하면 담당 기관에서 확인해주세요.",
                    List.of(new Option("NOT_APPLICABLE", "해당 없음 (첫 참여 포함)"), new Option("APPLIES", "해당함 · 2차례 연임"),
                            new Option("UNKNOWN", "모르겠어요"))),
            new Question("priorDisqualification", "과거 청정넷 활동에서 위촉 제한 사유가 있나요?",
                    "공고는 과거 위원 활동 중 징계·해촉 등의 전력이 있으면 위촉할 수 없다고 안내해요. 해당 여부가 불분명하면 담당 기관에서 확인해주세요.",
                    List.of(new Option("NONE", "없음 (첫 참여 포함)"), new Option("CONFIRMED", "있음 · 담당 기관 확인"),
                            new Option("UNKNOWN", "모르겠어요"))));

    public static boolean appliesAt(Instant now) { return now.atZone(ZoneId.of("Asia/Seoul")).getYear() == 2026; }
    public static Questionnaire questionnaire(long revision, Instant now) {
        return new Questionnaire(NUMBER, revision, VERSION, true, SCOPE,
                periodNotice(now) + " 이 공고의 연령·서울 거주 또는 생활권·위원 이력을 확인해요.", SOURCE, QUESTIONS);
    }
    public static Evaluation evaluate(long revision, Request input, Instant now) {
        if (!appliesAt(now)) throw new IllegalArgumentException("검토한 2026년 하반기 모집 기준만 사용할 수 있습니다.");
        var values = validatedAnswers(QUESTIONS, input.answers());
        var birth = values.get("birthRange");
        var connection = values.getOrDefault("seoulConnection", "UNKNOWN");
        var terms = values.get("consecutiveTerms");
        var disqualification = values.get("priorDisqualification");
        var connectionOutcome = switch (connection) {
            case "RESIDENT", "UNIVERSITY", "WORKPLACE", "BUSINESS_CONFIRMED" -> MET;
            case "NONE_CONFIRMED" -> NOT_MET;
            default -> UNKNOWN;
        };
        var checks = List.of(
                birthCheck(birth),
                check("seoulConnection", "서울 거주 또는 생활권", values.get("seoulConnection"), connectionOutcome,
                        "서울 거주자 또는 서울 소재 대학의 재·휴학생, 직장의 재·휴직자가 대상이에요. 서울 생활권 증빙은 재학·휴학·재직증명서, 사업자등록증 등으로 확인해요.",
                        "서울에 살지 않아도 서울 소재 대학·직장에 해당할 수 있어요. 서울 생활권 증빙을 확인해주세요."),
                check("consecutiveTerms", "연임 제한", terms, "NOT_APPLICABLE".equals(terms) ? MET : "APPLIES".equals(terms) ? NOT_MET : UNKNOWN,
                        "2023~2025년 2차례 연임한 위원은 이 모집에서 제외돼요.", "2023~2025년 위원 활동 이력과 연임 여부를 확인해주세요."),
                check("priorDisqualification", "과거 위원 활동에 따른 제한", disqualification, "NONE".equals(disqualification) ? MET : "CONFIRMED".equals(disqualification) ? NOT_MET : UNKNOWN,
                        "과거 위원 활동 중 징계·해촉 등의 전력이 있으면 위촉할 수 없어요.", "위촉 제한 사유에 해당하는지 담당 기관에서 확인해주세요."));
        var evidence = new SourceEvidence(SOURCE, SCOPE, Optional.empty());
        var conditions = new ArrayList<ConditionAssessment>();
        for (int i = 0; i < checks.size(); i++) {
            var check = checks.get(i);
            conditions.add(new ConditionAssessment("seoul-network-" + i, check.label(), Optional.of(check.providedValue()), Optional.empty(), check.outcome(),
                    check.outcome() == UNKNOWN ? Optional.of(ConditionAssessment.Uncertainty.MISSING_USER_INPUT) : Optional.empty(), check.explanation(), evidence));
        }
        var basis = new EvaluationBasis(NUMBER, Long.toString(revision), VERSION, now);
        var common = new EligibilityDecision(basis, PolicyReview.complete(), conditions).status();
        var remaining = List.of("신청서 3개 문항 총 500자 이상 작성과 거주·생활 증빙서류 제출 여부를 확인해야 해요.",
                "사전교육 이수 인증과 퀴즈 70점 이상 득점 여부는 별도 확인이 필요해요.",
                "우선선발은 별도 증빙으로 확인해요. 해당 서류를 내지 않으면 일반선발로 전환돼요.",
                "서류 적격 여부와 종합 평가에 따라 선발해요. 실제 선발 결과는 공식 발표에서 확인해주세요.");
        var whole = new EligibilityDecision(basis, PolicyReview.incomplete(remaining.stream()
                .map(message -> new PolicyReview.PendingIssue(message, evidence)).toList()), conditions);
        return new Evaluation(NUMBER, revision, VERSION, whole.status(), common, SCOPE,
                periodNotice(now) + " 연령·서울 거주 또는 생활권·위원 이력만 비교한 결과예요.", remaining, SOURCE, now, checks);
    }
    static Check ageCheck(java.time.LocalDate birthDate) {
        return birthCheck(birthDate.isBefore(java.time.LocalDate.of(1986, 1, 2)) ? "MILITARY_EXTENSION_PENDING"
                : birthDate.isAfter(java.time.LocalDate.of(2007, 1, 1)) ? "TOO_YOUNG" : "BASE_RANGE");
    }
    private static Check birthCheck(String birth) {
        var ageOutcome = switch (birth == null ? "" : birth) {
            case "BASE_RANGE", "MILITARY_EXTENSION_CONFIRMED" -> MET;
            case "TOO_YOUNG", "OLDER_NO_EXTENSION", "EXTENDED_LIMIT_EXCEEDED" -> NOT_MET;
            default -> UNKNOWN;
        };
        return check("birthRange", "공고의 연령 기준", birth, ageOutcome,
                        "2026.1.1. 기준 만 19~39세로, 1986.1.2.~2007.1.1. 출생자가 기본 대상이에요. 의무복무 제대군인의 연령 상한 연장은 최대 3세 범위에서 확인해요.",
                        "출생일 범위와 군복무에 따른 연령 연장 후 기준 충족 여부를 확인해주세요.");
    }
    static String periodNotice(Instant now) {
        if (!now.isBefore(CLOSE_AT)) return "이 모집은 2026년 5월 29일 17:00(서울)에 접수가 마감됐어요.";
        var period = "접수 기간은 2026년 5월 20일 09:00~5월 29일 17:00(서울)이에요.";
        return now.isBefore(OPEN_AT) ? "접수 전이에요. " + period : period;
    }
    private static Check check(String id, String label, String value, ConditionAssessment.Outcome outcome, String evidence, String unknownReason) {
        var provided = QUESTIONS.stream().filter(q -> q.id().equals(id)).flatMap(q -> q.options().stream())
                .filter(option -> option.value().equals(value)).map(Option::label).findFirst().orElse("미응답");
        var explanation = outcome == MET ? "입력한 답변은 이 조건을 충족해요."
                : outcome == NOT_MET ? "입력한 답변은 이 조건을 충족하지 않아요." : unknownReason;
        return new Check(label, provided, outcome, explanation, evidence);
    }
}
