package kr.youthpolicymate.policy.catalog;

import kr.youthpolicymate.eligibility.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static kr.youthpolicymate.eligibility.ConditionAssessment.Outcome.*;
import static kr.youthpolicymate.policy.catalog.PolicyQuestions.*;

/** 2026 국가근로장학금의 검토한 공통요건만 비교한다. 대학의 개별 심사를 대신하지 않는다. */
public final class WorkStudyRules {
    public static final String NUMBER = "20260821005400113348";
    public static final String CONTENT_HASH = "a1523aaa7fc8ac8097c70cf4830048c9c6f8430464864823206778f7c82c5e5d";
    public static final String VERSION = "work-study-2026-2-v1";
    public static final String SOURCE = "https://www.kosaf.go.kr/ko/scholar.do?pg=scholarship05_04_01";
    public static final String SCOPE = "2026년 2학기 국가근로장학금 신청 조건";
    private static final List<Option> YES_NO = List.of(new Option("YES", "예"), new Option("NO", "아니요"), new Option("UNKNOWN", "모르겠어요"));
    private static final List<Option> EXCEPTION = List.of(new Option("CONFIRMED", "적용 제외 · 재단·대학 확인 완료"),
            new Option("NONE", "적용 제외 대상 아님"), new Option("UNKNOWN", "모르겠어요"));
    private static final List<Question> QUESTIONS = List.of(
            new Question("nationality", "대한민국 국적을 가지고 있나요?", "국적과 거주지는 다른 조건이에요.", YES_NO),
            new Question("enrollment", "2026년 2학기 지원 대상 대학의 재학생 또는 입학예정자인가요?", "학교명이나 학번은 입력하지 않아요. 지원 대상 여부는 대학 안내에서 확인해주세요.", YES_NO),
            new Question("grade", "직전학기 성적은 100점 기준으로 몇 점인가요?", "대학이 제공한 백분위 성적을 확인해주세요. 4.5점 만점 평점을 직접 환산하지 마세요.",
                    List.of(new Option("AT_LEAST_70", "70점 이상"), new Option("BELOW_70", "70점 미만"), new Option("NOT_ISSUED", "직전학기 성적이 없어요"), new Option("UNKNOWN", "모르겠어요"))),
            new Question("gradeException", "성적 기준의 적용 제외를 확인받았나요?", "성적 기준을 적용받지 않는 대상인지 재단이나 대학에서 확인해주세요. 사유·증빙은 이곳에 제출하지 않아요.", EXCEPTION),
            new Question("income", "한국장학재단에서 확인한 2026년 2학기 학자금 지원구간은 몇 구간인가요?", "월급이나 가구 소득액으로 직접 환산하지 않아요.",
                    List.of(new Option("UP_TO_9", "기초·차상위 또는 1~9구간"), new Option("ABOVE_9", "9구간 초과"), new Option("NOT_CALCULATED", "아직 산정되지 않았어요"), new Option("UNKNOWN", "모르겠어요"))),
            new Question("incomeException", "학자금 지원구간 기준의 적용 제외를 확인받았나요?", "위기가구·일부 근로유형은 예외가 있어요. 재단이나 대학에서 적용 제외 여부를 확인해주세요.", EXCEPTION));

    public static Questionnaire questionnaire(long revision) {
        return new Questionnaire(NUMBER, revision, VERSION, true, SCOPE,
                "국적·학적·성적·학자금 지원구간을 확인해요. 대학별 선발요건과 참여 제한은 별도로 확인해주세요.", SOURCE, QUESTIONS);
    }

    public static Evaluation evaluate(long revision, Request input, Instant now) {
        var values = validatedAnswers(QUESTIONS, input.answers());
        var checks = List.of(
                yesNo("대한민국 국적", values.get("nationality"), "지원 대상은 대한민국 국적 보유자예요."),
                yesNo("지원 대상 대학의 학적", values.get("enrollment"), "지원 대상 대학의 재학생과 입학예정자를 확인해요."),
                threshold("직전학기 성적", values.get("grade"), values.get("gradeException"), "AT_LEAST_70", "BELOW_70",
                        "직전학기 백분위 70점 이상이 기준이며, 인정된 성적 적용 제외를 함께 확인해요."),
                threshold("학자금 지원구간", values.get("income"), values.get("incomeException"), "UP_TO_9", "ABOVE_9",
                        "2026년 2학기 재단 산정 9구간 이하가 기준이며, 인정된 적용 제외를 함께 확인해요."));
        var evidence = new SourceEvidence(SOURCE, SCOPE, Optional.empty());
        var conditions = new ArrayList<ConditionAssessment>();
        for (int i = 0; i < checks.size(); i++) {
            var check = checks.get(i);
            conditions.add(new ConditionAssessment("work-study-" + i, check.label(), Optional.of(check.providedValue()), Optional.empty(),
                    check.outcome(), check.outcome() == UNKNOWN ? Optional.of(ConditionAssessment.Uncertainty.MISSING_USER_INPUT) : Optional.empty(),
                    check.explanation(), evidence));
        }
        var basis = new EvaluationBasis(NUMBER, Long.toString(revision), VERSION, now);
        var common = new EligibilityDecision(basis, PolicyReview.complete(), conditions).status();
        var remaining = List.of("대학의 2026년 2학기 선발요건·참여 제한·중복 참여 기준을 확인해주세요.", "대학이 이번 차수에 신청을 받는지, 서류 제출·가구원 동의 기한은 언제인지 확인해주세요.");
        var whole = new EligibilityDecision(basis, PolicyReview.incomplete(remaining.stream()
                .map(message -> new PolicyReview.PendingIssue(message, evidence)).toList()), conditions);
        return new Evaluation(NUMBER, revision, VERSION, whole.status(), common, SCOPE,
                "국적·학적·성적·학자금 지원구간의 확인 결과예요. 대학별 기준과 예외는 별도로 확인해주세요.", remaining, SOURCE, now, checks);
    }

    private static Check yesNo(String label, String value, String evidence) {
        var outcome = "YES".equals(value) ? MET : "NO".equals(value) ? NOT_MET : UNKNOWN;
        return new Check(label, "YES".equals(value) ? "예" : "NO".equals(value) ? "아니요" : "미응답·모름", outcome,
                outcome == MET ? "입력한 답변이 이 요건에 해당해요." : outcome == NOT_MET ? "입력한 답변이 이 요건에 해당하지 않아요." : "이 항목을 확인해야 해요.", evidence);
    }
    private static Check threshold(String label, String value, String exception, String passing, String failing, String evidence) {
        var outcome = passing.equals(value) || "CONFIRMED".equals(exception) ? MET
                : failing.equals(value) && "NONE".equals(exception) ? NOT_MET : UNKNOWN;
        var explanation = passing.equals(value) ? "입력한 구간은 이 기준을 충족해요." : "CONFIRMED".equals(exception)
                ? "재단이나 대학에서 적용 제외를 확인받았다는 답변을 반영했어요."
                : outcome == NOT_MET ? "입력한 구간은 지원 기준에 맞지 않고, 적용 제외 대상에도 해당하지 않아요."
                : "성적·학자금 지원구간이나 적용 제외 여부를 확인한 뒤 다시 답해주세요.";
        var option = QUESTIONS.stream().flatMap(q -> q.options().stream()).filter(o -> o.value().equals(value)).findFirst();
        return new Check(label, option.map(Option::label).orElse("미응답"), outcome, explanation, evidence);
    }
}
