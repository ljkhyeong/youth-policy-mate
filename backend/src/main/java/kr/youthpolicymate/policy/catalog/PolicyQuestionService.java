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
        var policy = store.questionVersion(number).orElseThrow(PolicyNotFoundException::new);
        var hash = policy.contentHash();
        var reviewedHash = ReviewedPolicyQuestions.contentHashesAt(now).get(number);
        if (reviewedHash != null && reviewedHash.equals(hash)) {
            return switch (number) {
                case WorkStudyRules.NUMBER -> WorkStudyRules.questionnaire(policy.revision());
                case ExamFeeRules.NUMBER -> ExamFeeRules.questionnaire(policy.revision());
                case KPassRules.NUMBER -> KPassRules.questionnaire(policy.revision(), now);
                case YouthHousingSavingsRules.NUMBER -> YouthHousingSavingsRules.questionnaire(policy.revision());
                case SeoulYouthNetworkRules.NUMBER -> SeoulYouthNetworkRules.questionnaire(policy.revision(), now);
                case MovingFeeRules.NUMBER -> MovingFeeRules.questionnaire(policy.revision(), now);
                case YouthTomorrowSavingsRules.NUMBER -> YouthTomorrowSavingsRules.questionnaire(policy.revision(), now);
                case GuaranteeFeeRules.NUMBER -> GuaranteeFeeRules.questionnaire(policy.revision());
                default -> throw new IllegalStateException("등록한 정책의 질문 구현이 필요합니다.");
            };
        }
        if (ExamFeeRules.NUMBER.equals(number) && ExamFeeRules.CONTENT_HASH.equals(hash)) {
            return new PolicyQuestions.Questionnaire(number, policy.revision(), "", false, ExamFeeRules.SCOPE,
                    "올해 지원 기준의 질문은 아직 제공하지 않아요. 공식 안내를 확인해주세요.", ExamFeeRules.SOURCE, List.of());
        }
        if (KPassRules.NUMBER.equals(number) && KPassRules.CONTENT_HASH.equals(hash)) {
            return new PolicyQuestions.Questionnaire(number, policy.revision(), "", false, KPassRules.SCOPE,
                    "올해 가입·이용 기준의 질문은 아직 제공하지 않아요. 공식 안내를 확인해주세요.", KPassRules.SOURCE, List.of());
        }
        if (YouthHousingSavingsRules.NUMBER.equals(number) && YouthHousingSavingsRules.CONTENT_HASH.equals(hash)) {
            return new PolicyQuestions.Questionnaire(number, policy.revision(), "", false, YouthHousingSavingsRules.SCOPE,
                    "올해 가입 기준의 질문은 아직 제공하지 않아요. 공식 안내를 확인해주세요.", YouthHousingSavingsRules.SOURCE, List.of());
        }
        if (SeoulYouthNetworkRules.NUMBER.equals(number) && SeoulYouthNetworkRules.CONTENT_HASH.equals(hash)) {
            return new PolicyQuestions.Questionnaire(number, policy.revision(), "", false, SeoulYouthNetworkRules.SCOPE,
                    "이 질문은 2026년 하반기 모집 기준이에요. 새 모집의 질문은 아직 제공하지 않아요.", SeoulYouthNetworkRules.SOURCE, List.of());
        }
        if (MovingFeeRules.NUMBER.equals(number) && MovingFeeRules.CONTENT_HASH.equals(hash)) {
            return new PolicyQuestions.Questionnaire(number, policy.revision(), "", false, MovingFeeRules.SCOPE,
                    "이 질문은 2026년 상반기 모집 기준이에요. 새 모집의 질문은 아직 제공하지 않아요.", MovingFeeRules.SOURCE, List.of());
        }
        if (YouthTomorrowSavingsRules.NUMBER.equals(number) && YouthTomorrowSavingsRules.CONTENT_HASH.equals(hash)) {
            return new PolicyQuestions.Questionnaire(number, policy.revision(), "", false, YouthTomorrowSavingsRules.SCOPE,
                    "이 질문은 2026년 5월 신규 모집 기준이에요. 새 모집의 질문은 아직 제공하지 않아요.", YouthTomorrowSavingsRules.SOURCE, List.of());
        }
        if (GuaranteeFeeRules.NUMBER.equals(number) && GuaranteeFeeRules.CONTENT_HASH.equals(hash)) {
            return new PolicyQuestions.Questionnaire(number, policy.revision(), "", false, GuaranteeFeeRules.SCOPE,
                    "올해 보증료 지원 기준의 질문은 아직 제공하지 않아요. 공식 안내를 확인해주세요.", GuaranteeFeeRules.SOURCE, List.of());
        }
        return new PolicyQuestions.Questionnaire(number, policy.revision(), "", false, "신청 조건 확인",
                "이 정책의 조건 확인 질문은 아직 제공하지 않아요. 공식 안내를 확인해주세요.", PolicyCatalogStore.sourceUrl(number), List.of());
    }
    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public PolicyQuestions.Evaluation evaluate(String number, PolicyQuestions.Request request) {
        var now = clock.instant();
        var questions = questionsAt(number, now);
        if (!questions.available() || request.revision() != questions.revision() || !request.ruleVersion().equals(questions.ruleVersion())) throw new PolicyChangedException();
        return switch (number) {
            case WorkStudyRules.NUMBER -> WorkStudyRules.evaluate(questions.revision(), request, now);
            case ExamFeeRules.NUMBER -> ExamFeeRules.evaluate(questions.revision(), request, now);
            case KPassRules.NUMBER -> KPassRules.evaluate(questions.revision(), request, now);
            case YouthHousingSavingsRules.NUMBER -> YouthHousingSavingsRules.evaluate(questions.revision(), request, now);
            case SeoulYouthNetworkRules.NUMBER -> SeoulYouthNetworkRules.evaluate(questions.revision(), request, now);
            case MovingFeeRules.NUMBER -> MovingFeeRules.evaluate(questions.revision(), request, now);
            case YouthTomorrowSavingsRules.NUMBER -> YouthTomorrowSavingsRules.evaluate(questions.revision(), request, now);
            case GuaranteeFeeRules.NUMBER -> GuaranteeFeeRules.evaluate(questions.revision(), request, now);
            default -> throw new PolicyChangedException();
        };
    }
    public static class PolicyChangedException extends RuntimeException {}
}
