package kr.youthpolicymate.policy.catalog;

import kr.youthpolicymate.eligibility.*;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import static kr.youthpolicymate.eligibility.ConditionAssessment.Outcome.*;
import static kr.youthpolicymate.policy.catalog.PolicyQuestions.*;

/** 현재 월의 가입·이용 공통요건만 비교한다. 환급 유형·금액과 실제 지급은 공식 서비스에서 확인한다. */
public final class KPassRules {
    public static final String NUMBER = "20260710005400113257";
    public static final String CONTENT_HASH = "8285137b93b842eec33bd8f9ad79f8eef1c953858ebc9feca37e90140644e653";
    public static final String SOURCE = "https://korea-pass.kr/info/use_pay.do";
    public static final String SCOPE = "K-패스(모두의카드) 가입·이용 조건";
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final List<Question> QUESTIONS = List.of(
            new Question("age", "현재 만 19세 이상인가요?",
                    "기본 가입 연령을 확인해요. 만 35세 이상도 기본 가입 대상이므로 청년 환급률의 연령 범위와 구분해요. 생년월일 전체는 입력하지 않아요.",
                    List.of(new Option("ADULT", "만 19세 이상이에요"), new Option("UNDER_19", "만 19세 미만이에요"), new Option("UNKNOWN", "아직 확인하지 못했어요"))),
            new Question("registration", "공식 홈페이지나 앱에 회원가입하고 이용 중인 카드를 등록했나요?",
                    "카드 발급만으로는 적립되지 않아요. 모두의카드(K-패스) 회원가입과 실제 이용 카드의 등록 상태를 확인해주세요. 카드번호는 입력하지 않아요.",
                    List.of(new Option("REGISTERED", "회원가입과 이용 카드 등록을 모두 마쳤어요"), new Option("CARD_ONLY", "카드만 발급받았고 회원가입·등록은 하지 않았어요"),
                            new Option("NOT_REGISTERED", "회원가입이나 이용 카드 등록을 아직 마치지 않았어요"), new Option("UNKNOWN", "아직 확인하지 못했어요"))),
            new Question("residence", "공식 서비스에서 참여 지자체의 주민으로 확인됐나요?",
                    "서울을 포함한 참여 지자체 거주를 확인해요. 외국인도 가입할 수 있으며, 이 서비스에서는 국적·주소·증빙서류를 받지 않아요. 주소지 확인이 진행 중이면 그대로 선택해주세요.",
                    List.of(new Option("CONFIRMED", "참여 지자체의 주민으로 확인됐어요"), new Option("PENDING", "주소지 확인이 진행 중이에요"), new Option("UNKNOWN", "아직 확인하지 못했어요"))),
            new Question("monthlyRides", "이번 달의 인정 이용 횟수와 첫 가입 월 여부를 확인했나요?",
                    "화면에 표시된 연월의 공식 이용내역으로 답해주세요. 환승마다 따로 세거나 미반영 내역을 0회로 바꾸지 않아요. 이번 달 가입 여부가 불확실하면 ‘아직 확인하지 못했어요’를 선택해주세요.",
                    List.of(new Option("AT_LEAST_15", "인정 이용 횟수가 15회 이상이에요"),
                            new Option("FIRST_MONTH_1_TO_14", "가입 첫 달이며 인정 이용 횟수가 1~14회예요"),
                            new Option("LATER_MONTH_1_TO_14", "가입 첫 달이 아니며 인정 이용 횟수가 1~14회예요"),
                            new Option("ZERO", "인정 이용이 0회이고 반영을 기다리는 이용도 없어요"),
                            new Option("PENDING", "이용내역 반영을 기다리고 있어요"), new Option("UNKNOWN", "아직 확인하지 못했어요"))));

    public static boolean appliesAt(Instant now) { return now.atZone(SEOUL).getYear() == 2026; }
    public static String versionAt(Instant now) { return "k-pass-2026-v1-" + YearMonth.from(now.atZone(SEOUL)); }
    private static String scopeAt(Instant now) {
        var month = YearMonth.from(now.atZone(SEOUL));
        return month.getYear() + "년 " + month.getMonthValue() + "월 " + SCOPE;
    }
    public static Questionnaire questionnaire(long revision, Instant now) {
        return new Questionnaire(NUMBER, revision, versionAt(now), true, scopeAt(now),
                "이번 달의 연령·가입·주소지 확인·이용 횟수를 확인해요. 이용 내역이 늘면 결과가 달라질 수 있어요. 환급률과 금액은 K-패스에서 확인해주세요.", SOURCE, QUESTIONS);
    }
    public static Evaluation evaluate(long revision, Request input, Instant now) {
        if (!appliesAt(now)) throw new IllegalArgumentException("검토한 2026년 이용 기준만 사용할 수 있습니다.");
        var values = validatedAnswers(QUESTIONS, input.answers());
        var age = values.get("age"); var registration = values.get("registration"); var residence = values.get("residence"); var rides = values.get("monthlyRides");
        var checks = List.of(
                check("age", "기본 가입 연령", age, "ADULT".equals(age) ? MET : "UNDER_19".equals(age) ? NOT_MET : UNKNOWN,
                        "기본 가입 연령은 만 19세 이상이에요. 만 35세 이상도 가입할 수 있고, 청년 환급률은 만 19~34세에 적용돼요. 근거: https://korea-pass.kr/info/use_join.do"),
                check("registration", "회원가입과 이용 카드 등록", registration, "REGISTERED".equals(registration) ? MET : List.of("CARD_ONLY", "NOT_REGISTERED").contains(registration == null ? "" : registration) ? NOT_MET : UNKNOWN,
                        "적립을 받으려면 카드 발급 후 공식 홈페이지나 앱에 회원가입하고 카드를 등록해야 해요. 가입하지 않고 이용한 카드에는 환급금이 발생하지 않는다고 안내돼 있어요. 근거: https://korea-pass.kr/info/use_accm.do"),
                check("residence", "참여 지자체 거주 확인", residence, "CONFIRMED".equals(residence) ? MET : UNKNOWN,
                        "참여 지자체의 주민이 가입 대상이며 외국인도 가입할 수 있어요. 주소지 확인이 끝나야 거주 조건을 확인할 수 있어요. 근거: https://korea-pass.kr/info/use_join.do"),
                check("monthlyRides", "이번 달 인정 이용 횟수", rides, List.of("AT_LEAST_15", "FIRST_MONTH_1_TO_14").contains(rides == null ? "" : rides) ? MET
                                : List.of("LATER_MONTH_1_TO_14", "ZERO").contains(rides == null ? "" : rides) ? NOT_MET : UNKNOWN,
                        "매월 1일부터 말일까지 인정 이용을 비교하며 보통 15회가 필요해요. 가입 첫 달에는 15회 미만 이용에도 예외가 있어요. 환승을 각각 별도 횟수로 세지 않아요. 근거: " + SOURCE));
        var evidence = new SourceEvidence(SOURCE, scopeAt(now), Optional.empty());
        var conditions = new ArrayList<ConditionAssessment>();
        for (int i = 0; i < checks.size(); i++) {
            var check = checks.get(i);
            conditions.add(new ConditionAssessment("k-pass-" + i, check.label(), Optional.of(check.providedValue()), Optional.empty(), check.outcome(),
                    check.outcome() == UNKNOWN ? Optional.of(ConditionAssessment.Uncertainty.MISSING_USER_INPUT) : Optional.empty(), check.explanation(), evidence));
        }
        var basis = new EvaluationBasis(NUMBER, Long.toString(revision), versionAt(now), now);
        var common = new EligibilityDecision(basis, PolicyReview.complete(), conditions).status();
        var remaining = List.of("공식 서비스의 본인·주소지·카드 등록 결과와 실제 적립 대상 이용내역을 확인해주세요. 이 서비스는 해당 계정이나 카드 이용내역을 조회하지 않아요.",
                "시외·고속·공항버스, KTX·SRT 등 별도 발권 수단은 적립 대상에서 제외돼요. 공항철도와 공항버스를 구분하고 실제 인정 내역은 공식 서비스에서 확인해주세요.",
                "청년·다자녀·저소득 등 대상 구분, 지자체 추가 혜택, 한시 혜택과 환급 방식에 따라 금액이 달라져요. 환급률과 금액은 K-패스에서 확인해주세요.",
                "현재 월의 중간 확인 결과예요. 이용·반영 내역이 늘면 다시 확인해주세요. 최종 정산 결과와 카드사의 실제 지급일·지급 방식을 별도로 확인해주세요.");
        var whole = new EligibilityDecision(basis, PolicyReview.incomplete(remaining.stream()
                .map(message -> new PolicyReview.PendingIssue(message, evidence)).toList()), conditions);
        return new Evaluation(NUMBER, revision, versionAt(now), whole.status(), common, scopeAt(now),
                "이번 달 가입·이용 조건의 확인 결과예요. 실제 적립 내역과 환급액·지급 여부는 K-패스에서 확인해주세요.", remaining, SOURCE, now, checks);
    }
    private static Check check(String id, String label, String value, ConditionAssessment.Outcome outcome, String evidence) {
        var provided = QUESTIONS.stream().filter(q -> q.id().equals(id)).flatMap(q -> q.options().stream())
                .filter(option -> option.value().equals(value)).map(Option::label).findFirst().orElse("미응답");
        String explanation;
        if (outcome == UNKNOWN) explanation = "PENDING".equals(value) ? "K-패스에서 주소지 확인이나 이용내역 반영이 끝나면 다시 답해주세요."
                : "이 항목은 아직 확인할 수 없어요. 공식 안내와 본인 정보를 확인한 뒤 다시 답해주세요.";
        else if (id.equals("monthlyRides") && outcome == NOT_MET) explanation = "현재 이용 횟수는 기준에 못 미쳐요. 월말까지 이용 내역이 늘면 결과가 달라질 수 있어요.";
        else if ("FIRST_MONTH_1_TO_14".equals(value)) explanation = "가입 첫 달에는 15회 미만도 인정돼요. 다음 달부터는 이용 횟수 기준을 다시 확인해주세요.";
        else explanation = outcome == MET ? "입력한 답변은 이 조건을 충족해요."
                : "입력한 답변은 이 조건을 충족하지 않아요. 연령이나 가입·등록 상태가 바뀌면 다시 확인해주세요.";
        return new Check(label, provided, outcome, explanation, evidence);
    }
}
