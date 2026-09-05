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
        var policy = store.find(number).orElseThrow(PolicyNotFoundException::new);
        var hash = store.contentHash(number);
        if (WorkStudyRules.NUMBER.equals(number) && WorkStudyRules.CONTENT_HASH.equals(hash)) {
            return WorkStudyRules.questionnaire(policy.revision());
        }
        if (ExamFeeRules.NUMBER.equals(number) && ExamFeeRules.CONTENT_HASH.equals(hash)) {
            if (ExamFeeRules.appliesAt(now)) return ExamFeeRules.questionnaire(policy.revision());
            return new PolicyQuestions.Questionnaire(number, policy.revision(), "", false, ExamFeeRules.SCOPE,
                    "2026년 지원 기준만 검토했어요. 현재 연도에 적용할 기준은 공식 안내에서 다시 확인해주세요.", ExamFeeRules.SOURCE, List.of());
        }
        return new PolicyQuestions.Questionnaire(number, policy.revision(), "", false, "원문 조건 확인",
                "현재 원문에 맞는 질문을 아직 검토하지 못했어요. 변경된 정책에는 이전 질문을 적용하지 않아요.", policy.sourceUrl(), List.of());
    }
    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public PolicyQuestions.Evaluation evaluate(String number, PolicyQuestions.Request request) {
        var now = clock.instant();
        var questions = questionsAt(number, now);
        if (!questions.available() || request.revision() != questions.revision() || !request.ruleVersion().equals(questions.ruleVersion())) throw new PolicyChangedException();
        return switch (number) {
            case WorkStudyRules.NUMBER -> WorkStudyRules.evaluate(questions.revision(), request, now);
            case ExamFeeRules.NUMBER -> ExamFeeRules.evaluate(questions.revision(), request, now);
            default -> throw new PolicyChangedException();
        };
    }
    public static class PolicyChangedException extends RuntimeException {}
}
