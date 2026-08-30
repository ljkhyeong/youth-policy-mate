package kr.youthpolicymate.eligibility;

import java.util.Objects;
import java.util.Optional;

public record SourceEvidence(String sourceReference, String location, Optional<String> excerpt) {

    public SourceEvidence {
        if (sourceReference == null || sourceReference.isBlank() || location == null || location.isBlank()) {
            throw new IllegalArgumentException("근거 자료 참조와 확인한 위치가 필요합니다.");
        }
        // 원문 필드 누락은 빈 근거 문구를 만들어 채우지 않고 별도로 표현한다.
        excerpt = Objects.requireNonNull(excerpt, "원문 발췌의 존재 여부가 필요합니다.");
    }
}
