package kr.youthpolicymate.eligibility;

import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

public sealed interface AgeCondition {

    String conditionId();

    String appliedCondition();

    SourceEvidence evidence();

    // 원문에서 만 나이, 양쪽 경계 포함과 기준일을 확인한 조건만 사용한다.
    record CompletedYears(
            String conditionId,
            int minimumInclusive,
            int maximumInclusive,
            LocalDate referenceDate,
            SourceEvidence evidence
    ) implements AgeCondition {

        public CompletedYears {
            if (conditionId == null || conditionId.isBlank()) {
                throw new IllegalArgumentException("연령 조건 식별자가 필요합니다.");
            }
            if (minimumInclusive < 0 || maximumInclusive < minimumInclusive) {
                throw new IllegalArgumentException("연령은 0세 이상이며 최대 연령은 최소 연령 이상이어야 합니다.");
            }
            referenceDate = Objects.requireNonNull(referenceDate, "확인한 연령 기준일이 필요합니다.");
            evidence = Objects.requireNonNull(evidence, "연령 조건의 원문 근거가 필요합니다.");
        }

        @Override
        public String appliedCondition() {
            return referenceDate + " 기준 만 " + minimumInclusive + "세 이상 " + maximumInclusive + "세 이하";
        }
    }

    record Unresolved(
            String conditionId,
            String appliedCondition,
            Optional<LocalDate> referenceDate,
            String reason,
            SourceEvidence evidence
    ) implements AgeCondition {

        public Unresolved {
            if (conditionId == null || conditionId.isBlank()
                    || appliedCondition == null || appliedCondition.isBlank()
                    || reason == null || reason.isBlank()) {
                throw new IllegalArgumentException("연령 조건 식별자, 확인할 조건과 미해석 이유가 필요합니다.");
            }
            referenceDate = Objects.requireNonNull(referenceDate, "연령 기준일의 존재 여부가 필요합니다.");
            evidence = Objects.requireNonNull(evidence, "미해석 연령 조건의 원문 근거가 필요합니다.");
        }
    }
}
