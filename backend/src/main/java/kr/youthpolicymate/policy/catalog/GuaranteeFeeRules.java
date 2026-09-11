package kr.youthpolicymate.policy.catalog;

import kr.youthpolicymate.eligibility.*;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import static kr.youthpolicymate.eligibility.ConditionAssessment.Outcome.*;
import static kr.youthpolicymate.policy.catalog.PolicyQuestions.*;

/** 보증료 지원의 일부 조건을 비교하며 지급액·예산·서류 심사는 신청처에서 확인한다. */
public final class GuaranteeFeeRules {
    public static final String NUMBER = "20260527005400113223";
    public static final String CONTENT_HASH = "4dc5e18b6a09b35f00cf00c7be3a608689f6c2d33ee8b191c38a9823dac8cc24";
    public static final String VERSION = "guarantee-fee-2026-v1";
    public static final String SOURCE = "https://www.gov.kr/portal/rcvfvrSvc/dtlEx/161300000103";
    public static final String SCOPE = "2026년 서울 보증료 지원 공통 조건";
    private static final List<Question> QUESTIONS = List.of(
            new Question("guarantee", "신청일에 유효한 반환보증에 가입하고 보증료를 냈나요?",
                    "HUG·HF·SGI의 전세보증금반환보증 기준이에요. 전세대출 보증이나 임대인의 임대보증금보증과 구분해주세요.",
                    List.of(new Option("VALID_PAID", "유효한 반환보증 · 납부 완료"),
                            new Option("NOT_JOINED", "미가입 또는 다른 보증만 가입"),
                            new Option("EXPIRED", "반환보증 만료·해지"),
                            new Option("UNPAID", "보증료 미납"),
                            new Option("PENDING", "가입·갱신·납부 확인 중"),
                            new Option("UNKNOWN", "모르겠어요"))),
            new Question("deposit", "임대차계약서의 보증금은 얼마인가요?",
                    "전세대출 잔액이나 낸 보증료가 아닌 임차보증금이에요.",
                    List.of(new Option("UP_TO_300M", "3억 원 이하"), new Option("OVER_300M", "3억 원 초과"),
                            new Option("UNKNOWN", "모르겠어요"))),
            new Question("homeOwnership", "본인과 배우자 모두 무주택자인가요?",
                    "신청일 기준으로 확인해요. 분양권·입주권도 포함하며, 미혼이면 본인만 확인해주세요.",
                    List.of(new Option("NO_HOME", "모두 무주택"), new Option("OWNS_HOME", "주택·분양권·입주권 소유"),
                            new Option("UNKNOWN", "모르겠어요"))),
            new Question("applicantType", "신청일에 어떤 지원 유형에 해당하나요?",
                    "혼인신고 7년 이내면 나이와 관계없이 신혼부부를 선택해요. 그 외 서울 청년은 만 19~39세예요. 유형이 불분명하면 신청처에서 확인해주세요.",
                    List.of(new Option("NEWLYWED", "신혼부부 · 혼인신고 7년 이내"),
                            new Option("YOUTH", "만 19~39세 · 신혼부부 아님"),
                            new Option("OTHER", "청년·신혼부부에 해당 안 됨"),
                            new Option("UNKNOWN", "모르겠어요"))),
            new Question("incomeBasis", "신청용 소득서류의 기준을 확인했나요?",
                    "신청처 기준의 소득 합산 범위·대상 연도·증빙을 확인해요. 기혼자는 배우자 서류도 필요해요. 월급이나 매출로 대신하지 마세요.",
                    List.of(new Option("CONFIRMED", "소득 합산 범위·서류 확인"),
                            new Option("PENDING", "소득서류·적용 기준 확인 중"),
                            new Option("UNKNOWN", "모르겠어요"))),
            new Question("annualIncome", "신청용 서류로 확인한 연소득은 얼마인가요?",
                    "신혼부부는 부부 합산 연소득이에요. 소득이 없다면 신고사실없음 등 신청처가 인정하는 증빙도 확인해주세요.",
                    List.of(new Option("UP_TO_50M", "5,000만 원 이하"),
                            new Option("OVER_50_TO_60M", "5천만 초과~6천만 원 이하"),
                            new Option("OVER_60_TO_75M", "6천만 초과~7천5백만 원 이하"),
                            new Option("OVER_75M", "7,500만 원 초과"),
                            new Option("UNKNOWN", "모르겠어요"))));

    public static boolean appliesAt(Instant now) { return now.atZone(ZoneId.of("Asia/Seoul")).getYear() == 2026; }

    public static Questionnaire questionnaire(long revision) {
        return new Questionnaire(NUMBER, revision, VERSION, true, SCOPE,
                "보증 가입·보증금·무주택·소득을 확인해요. 청년 외 연령도 대상이며, 접수와 예산은 주소지 신청처에서 확인해주세요.", SOURCE, QUESTIONS);
    }

    public static Evaluation evaluate(long revision, Request input, Instant now) {
        if (!appliesAt(now)) throw new IllegalArgumentException("검토한 2026년 지원 기준만 사용할 수 있습니다.");
        var values = validatedAnswers(QUESTIONS, input.answers());
        var guarantee = values.getOrDefault("guarantee", "UNKNOWN");
        var deposit = values.getOrDefault("deposit", "UNKNOWN");
        var home = values.getOrDefault("homeOwnership", "UNKNOWN");
        var checks = List.of(
                check("반환보증 가입·납부", provided("guarantee", values), switch (guarantee) {
                    case "VALID_PAID" -> MET;
                    case "NOT_JOINED", "EXPIRED", "UNPAID" -> NOT_MET;
                    default -> UNKNOWN;
                }, "신청일에 유효한 HUG·HF·SGI 전세보증금반환보증에 가입하고 보증료를 납부해야 해요.",
                        "보증 종류·유효기간과 납부 여부를 확인해주세요. 처리 중인 가입·갱신을 완료로 보지 않아요."),
                check("임차보증금", provided("deposit", values), "UP_TO_300M".equals(deposit) ? MET : "OVER_300M".equals(deposit) ? NOT_MET : UNKNOWN,
                        "임차보증금은 3억 원 이하여야 해요.", "임대차계약서의 보증금을 확인해주세요."),
                check("본인·배우자 무주택", provided("homeOwnership", values), "NO_HOME".equals(home) ? MET : "OWNS_HOME".equals(home) ? NOT_MET : UNKNOWN,
                        "신청인과 배우자 모두 무주택이어야 하며 분양권·입주권도 소유 여부에 포함해요.",
                        "본인과 배우자의 주택·분양권·입주권 소유 여부를 확인해주세요."),
                incomeCheck(values));
        var evidence = new SourceEvidence(SOURCE, SCOPE, Optional.empty());
        var conditions = new ArrayList<ConditionAssessment>();
        for (int i = 0; i < checks.size(); i++) {
            var check = checks.get(i);
            conditions.add(new ConditionAssessment("guarantee-fee-" + i, check.label(), Optional.of(check.providedValue()), Optional.empty(), check.outcome(),
                    check.outcome() == UNKNOWN ? Optional.of(ConditionAssessment.Uncertainty.MISSING_USER_INPUT) : Optional.empty(), check.explanation(), evidence));
        }
        var basis = new EvaluationBasis(NUMBER, Long.toString(revision), VERSION, now);
        var common = new EligibilityDecision(basis, PolicyReview.complete(), conditions).status();
        var remaining = List.of("신청일 주소·거주와 계약서·보증서의 명의, 국적·재외국민 기준을 주소지 신청처에서 확인해주세요.",
                "등록임대사업자의 임대주택, 법인 임차인과 이미 지원받은 동일 보증서의 재신청은 지원 제외 대상이에요.",
                "주택 소유·지원 유형·소득서류와 그 밖의 지원 제외 사유는 신청처 심사가 필요해요.",
                "예산 소진 시 접수가 끝날 수 있어요. 보증 가입일·유형·납부액에 따른 실제 지원액과 지급 여부는 신청처에서 확인해주세요.");
        var whole = new EligibilityDecision(basis, PolicyReview.incomplete(remaining.stream()
                .map(message -> new PolicyReview.PendingIssue(message, evidence)).toList()), conditions);
        return new Evaluation(NUMBER, revision, VERSION, whole.status(), common, SCOPE,
                "보증 가입·보증금·무주택·소득의 확인 결과예요. 신청 자격과 지급액은 주소지 신청처에서 최종 확인해주세요.",
                remaining, SOURCE, now, checks);
    }

    private static Check incomeCheck(Map<String, String> values) {
        var type = values.getOrDefault("applicantType", "UNKNOWN");
        var amount = values.getOrDefault("annualIncome", "UNKNOWN");
        var outcome = UNKNOWN;
        if ("CONFIRMED".equals(values.get("incomeBasis"))) {
            outcome = switch (type) {
                case "YOUTH" -> switch (amount) {
                    case "UP_TO_50M" -> MET;
                    case "OVER_50_TO_60M", "OVER_60_TO_75M", "OVER_75M" -> NOT_MET;
                    default -> UNKNOWN;
                };
                case "OTHER" -> switch (amount) {
                    case "UP_TO_50M", "OVER_50_TO_60M" -> MET;
                    case "OVER_60_TO_75M", "OVER_75M" -> NOT_MET;
                    default -> UNKNOWN;
                };
                case "NEWLYWED" -> switch (amount) {
                    case "UP_TO_50M", "OVER_50_TO_60M", "OVER_60_TO_75M" -> MET;
                    case "OVER_75M" -> NOT_MET;
                    default -> UNKNOWN;
                };
                default -> UNKNOWN;
            };
        }
        return check("유형별 연소득", provided("applicantType", values) + " / " + provided("incomeBasis", values) + " / " + provided("annualIncome", values), outcome,
                "청년은 연 5,000만 원, 청년 외는 6,000만 원, 신혼부부는 부부 합산 7,500만 원 이하여야 해요.",
                "지원 유형과 신청용 소득의 합산 범위·대상 연도·증빙을 확인해주세요. 기준이 미확인이면 금액만으로 비교하지 않아요.");
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
