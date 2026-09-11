package kr.youthpolicymate.policy.catalog;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

final class ReviewedPolicyQuestions {
    private ReviewedPolicyQuestions() {}

    // 목록 필터와 실제 질문 제공에서 같은 원문·적용 시점 기준을 사용한다.
    static Map<String, String> contentHashesAt(Instant now) {
        var hashes = new LinkedHashMap<String, String>();
        hashes.put(WorkStudyRules.NUMBER, WorkStudyRules.CONTENT_HASH);
        if (ExamFeeRules.appliesAt(now)) hashes.put(ExamFeeRules.NUMBER, ExamFeeRules.CONTENT_HASH);
        if (KPassRules.appliesAt(now)) hashes.put(KPassRules.NUMBER, KPassRules.CONTENT_HASH);
        if (YouthHousingSavingsRules.appliesAt(now)) hashes.put(YouthHousingSavingsRules.NUMBER, YouthHousingSavingsRules.CONTENT_HASH);
        if (SeoulYouthNetworkRules.appliesAt(now)) hashes.put(SeoulYouthNetworkRules.NUMBER, SeoulYouthNetworkRules.CONTENT_HASH);
        if (MovingFeeRules.appliesAt(now)) hashes.put(MovingFeeRules.NUMBER, MovingFeeRules.CONTENT_HASH);
        if (YouthTomorrowSavingsRules.appliesAt(now)) hashes.put(YouthTomorrowSavingsRules.NUMBER, YouthTomorrowSavingsRules.CONTENT_HASH);
        if (GuaranteeFeeRules.appliesAt(now)) hashes.put(GuaranteeFeeRules.NUMBER, GuaranteeFeeRules.CONTENT_HASH);
        if (HaetsalronYouthRules.appliesAt(now)) hashes.put(HaetsalronYouthRules.NUMBER, HaetsalronYouthRules.CONTENT_HASH);
        if (MisoYouthFutureRules.appliesAt(now)) hashes.put(MisoYouthFutureRules.NUMBER, MisoYouthFutureRules.CONTENT_HASH);
        if (FutureYouthJobsRules.appliesAt(now)) hashes.put(FutureYouthJobsRules.NUMBER, FutureYouthJobsRules.CONTENT_HASH);
        return hashes;
    }
}
