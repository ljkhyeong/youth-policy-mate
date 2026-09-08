package kr.youthpolicymate.policy.catalog;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

final class BasicConditionRules {
    private BasicConditionRules() {}

    // 질문과 같은 비교 함수를 사용하며, SQL 정렬과 결과 표시에도 같은 비교값을 전달한다.
    static Map<String, Comparison> compare(BasicConditions input, Instant now) {
        var comparisons = new LinkedHashMap<String, Comparison>();
        var birth = input.birthDate();
        if (ExamFeeRules.appliesAt(now)) comparisons.put(ExamFeeRules.NUMBER,
                new Comparison(ExamFeeRules.CONTENT_HASH, ExamFeeRules.VERSION, ExamFeeRules.SOURCE, ExamFeeRules.ageCheck(birth), ""));
        if (KPassRules.appliesAt(now)) comparisons.put(KPassRules.NUMBER,
                new Comparison(KPassRules.CONTENT_HASH, KPassRules.versionAt(now), KPassRules.SOURCE, KPassRules.ageCheck(birth, now), ""));
        if (SeoulYouthNetworkRules.appliesAt(now)) comparisons.put(SeoulYouthNetworkRules.NUMBER,
                new Comparison(SeoulYouthNetworkRules.CONTENT_HASH, SeoulYouthNetworkRules.VERSION, SeoulYouthNetworkRules.SOURCE,
                        SeoulYouthNetworkRules.ageCheck(birth), SeoulYouthNetworkRules.periodNotice(now)));
        if (MovingFeeRules.appliesAt(now)) comparisons.put(MovingFeeRules.NUMBER,
                new Comparison(MovingFeeRules.CONTENT_HASH, MovingFeeRules.VERSION, MovingFeeRules.SOURCE,
                        MovingFeeRules.ageCheck(birth), MovingFeeRules.periodNotice(now)));
        if (YouthHousingSavingsRules.appliesAt(now)) comparisons.put(YouthHousingSavingsRules.NUMBER,
                new Comparison(YouthHousingSavingsRules.CONTENT_HASH, YouthHousingSavingsRules.VERSION, YouthHousingSavingsRules.SOURCE,
                        YouthHousingSavingsRules.ageCheck(birth, now),
                        "오늘(서울 날짜) 가입하는 경우의 연령만 비교했어요. 실제 가입일이 다르면 다시 확인해주세요."));
        if (YouthTomorrowSavingsRules.appliesAt(now)) comparisons.put(YouthTomorrowSavingsRules.NUMBER,
                new Comparison(YouthTomorrowSavingsRules.CONTENT_HASH, YouthTomorrowSavingsRules.VERSION, YouthTomorrowSavingsRules.SOURCE,
                        YouthTomorrowSavingsRules.ageCheck(birth), YouthTomorrowSavingsRules.periodNotice(now)
                                + " 수집 안내와 소득·출생일 기준이 달라 2026년 사업 지침을 적용했어요."));
        return comparisons;
    }

    record Comparison(String contentHash, String ruleVersion, String sourceUrl, PolicyQuestions.Check age, String periodNotice) {
        int priority() {
            return switch (age.outcome()) { case MET -> 0; case UNKNOWN -> 1; case NOT_MET -> 2; };
        }
    }
}
