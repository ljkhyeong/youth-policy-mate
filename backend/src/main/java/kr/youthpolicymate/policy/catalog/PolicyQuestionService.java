package kr.youthpolicymate.policy.catalog;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.util.List;

@Service
@Profile("!preview")
public class PolicyQuestionService {
    private final PolicyCatalogStore store;
    private final Clock clock;
    public PolicyQuestionService(PolicyCatalogStore store, Clock clock) { this.store = store; this.clock = clock; }

    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public PolicyQuestions.Questionnaire questions(String number) {
        var policy = store.find(number).orElseThrow(PolicyNotFoundException::new);
        if (WorkStudyRules.NUMBER.equals(number) && WorkStudyRules.CONTENT_HASH.equals(store.contentHash(number))) {
            return WorkStudyRules.questionnaire(policy.revision());
        }
        return new PolicyQuestions.Questionnaire(number, policy.revision(), "", false, "원문 조건 확인",
                "현재 원문에 맞는 질문을 아직 검토하지 못했어요. 변경된 정책에는 이전 질문을 적용하지 않아요.", policy.sourceUrl(), List.of());
    }
    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public PolicyQuestions.Evaluation evaluate(String number, PolicyQuestions.Request request) {
        var questions = questions(number);
        if (!questions.available() || request.revision() != questions.revision() || !request.ruleVersion().equals(questions.ruleVersion())) throw new PolicyChangedException();
        return WorkStudyRules.evaluate(questions.revision(), request, clock.instant());
    }
    public static class PolicyChangedException extends RuntimeException {}
}
