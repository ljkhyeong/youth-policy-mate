package kr.youthpolicymate.policy.catalog;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Service
@Profile("!preview")
public class PolicyQuestionService {
    private final PolicyCatalogStore store;
    private final Clock clock;
    public PolicyQuestionService(PolicyCatalogStore store, Clock clock) { this.store = store; this.clock = clock; }

    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public PolicyQuestions.Questionnaire questions(String number) {
        return questionsAt(number, clock.instant());
    }
    private PolicyQuestions.Questionnaire questionsAt(String number, Instant now) {
        return questionsAt(number, store.questionVersion(number).orElseThrow(PolicyNotFoundException::new), now);
    }
    private PolicyQuestions.Questionnaire questionsAt(String number, PolicyCatalogStore.QuestionVersion policy, Instant now) {
        if (policy.definition() != null) return policy.definition().questionnaire(policy.revision(), policy.contentHash(), now);
        return new PolicyQuestions.Questionnaire(number, policy.revision(), "", false, "신청 조건 확인",
                "이 정책의 조건 확인 질문은 아직 제공하지 않아요. 공식 안내를 확인해주세요.", PolicyCatalogStore.sourceUrl(number), List.of());
    }
    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public PolicyQuestions.Evaluation evaluate(String number, PolicyQuestions.Request request) {
        var now = clock.instant();
        var policy = store.questionVersion(number).orElseThrow(PolicyNotFoundException::new);
        var questions = questionsAt(number, policy, now);
        if (!questions.available() || request.revision() != questions.revision() || !request.ruleVersion().equals(questions.ruleVersion())) throw new PolicyChangedException();
        if (policy.definition() != null) return policy.definition().evaluate(policy.revision(), request, now);
        throw new PolicyChangedException();
    }
    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public PolicyQuestions.Prefill prefill(String number, PolicyQuestions.PrefillRequest input) {
        var now = clock.instant();
        if (input.birthDate().getYear() < 1 || input.birthDate().isAfter(now.atZone(java.time.ZoneId.of("Asia/Seoul")).toLocalDate()))
            throw new IllegalArgumentException("생년월일을 확인해주세요.");
        var policy = store.questionVersion(number).orElseThrow(PolicyNotFoundException::new);
        var questions = questionsAt(number, policy, now);
        if (!questions.available() || input.revision() != questions.revision() || !input.ruleVersion().equals(questions.ruleVersion()))
            throw new PolicyChangedException();
        return new PolicyQuestions.Prefill(policy.definition() == null ? List.of() : policy.definition().prefill(input.birthDate(), now));
    }
    public static class PolicyChangedException extends RuntimeException {}
}
