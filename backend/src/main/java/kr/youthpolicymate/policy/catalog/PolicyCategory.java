package kr.youthpolicymate.policy.catalog;

import java.util.List;

// 온통청년 대분류. 원천은 반각 가운뎃점(･)을 쓰므로 일반 가운뎃점 표기도 함께 비교한다.
public enum PolicyCategory {
    JOB("일자리"), HOUSING("주거"), EDUCATION("교육･직업훈련"), FINANCE("금융･복지･문화"), PARTICIPATION("참여･기반");

    private final String source;

    PolicyCategory(String source) { this.source = source; }

    List<String> labels() { return List.of(source, source.replace('･', '·')); }
}
