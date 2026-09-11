package kr.youthpolicymate.policy.catalog;

record PolicyAgeComparison(String contentHash, String ruleVersion, String sourceUrl, PolicyQuestions.Check age, String periodNotice) {
    int priority() { return switch (age.outcome()) { case MET -> 0; case UNKNOWN -> 1; case NOT_MET -> 2; }; }
}
