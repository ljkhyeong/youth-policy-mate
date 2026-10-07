package kr.youthpolicymate.eligibility;

/** 조건 항목 하나의 비교 결과. 미확인(UNKNOWN)은 불충족으로 합치지 않는다. */
public enum ConditionOutcome {
    MET,
    NOT_MET,
    UNKNOWN
}
