package kr.youthpolicymate.policy.catalog;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static kr.youthpolicymate.policy.SeoulTime.SEOUL;

@Service
@Profile("!preview")
public class PolicyQuestionService {
    private final PolicyCatalogStore store;
    private final Clock clock;
    public PolicyQuestionService(PolicyCatalogStore store, Clock clock) { this.store = store; this.clock = clock; }

    public PolicyQuestions.Questionnaire questions(String number) {
        var policy = store.questionVersion(number).orElseThrow(PolicyNotFoundException::new);
        if (policy.definition() != null) return policy.definition().questionnaire(policy.revision(), policy.contentHash(), clock.instant());
        return new PolicyQuestions.Questionnaire(number, policy.revision(), "", false, "신청 조건 확인",
                "이 정책의 조건 확인 질문은 아직 제공하지 않아요. 공식 안내를 확인해주세요.", PolicyCatalogStore.sourceUrl(number), List.of());
    }
    public PolicyQuestions.Evaluation evaluate(String number, PolicyQuestions.Request request) {
        var now = clock.instant();
        var policy = current(number, request.revision(), request.ruleVersion(), now);
        return policy.definition().evaluate(policy.revision(), request, now);
    }
    public PolicyQuestions.Prefill prefill(String number, PolicyQuestions.PrefillRequest input) {
        var now = clock.instant();
        BasicConditions.checkBirthDate(input.birthDate(), LocalDate.ofInstant(now, SEOUL));
        return new PolicyQuestions.Prefill(current(number, input.revision(), input.ruleVersion(), now).definition().prefill(input.birthDate(), now));
    }
    // 현재 원문·적용 기간에 질문을 제공하고 요청한 개정·규칙 버전이 같을 때만 규칙을 사용한다.
    private PolicyCatalogStore.QuestionVersion current(String number, long revision, String ruleVersion, Instant now) {
        var policy = store.questionVersion(number).orElseThrow(PolicyNotFoundException::new);
        if (policy.definition() == null) throw new PolicyChangedException();
        var questions = policy.definition().questionnaire(policy.revision(), policy.contentHash(), now);
        if (!questions.available() || revision != questions.revision() || !ruleVersion.equals(questions.ruleVersion())) throw new PolicyChangedException();
        return policy;
    }
    public static class PolicyChangedException extends RuntimeException {}
}
