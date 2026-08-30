package kr.youthpolicymate.eligibility;

import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public sealed interface ResidenceCondition {

    String conditionId();

    String appliedCondition();

    SourceEvidence evidence();

    // 원문에서 주민등록상 거주 범위와 기준일을 확인한 조건만 사용한다.
    record RegisteredIn(
            String conditionId,
            Area area,
            Set<SeoulDistrict> districts,
            LocalDate referenceDate,
            SourceEvidence evidence
    ) implements ResidenceCondition {

        public RegisteredIn {
            if (conditionId == null || conditionId.isBlank()) {
                throw new IllegalArgumentException("거주 조건 식별자가 필요합니다.");
            }
            area = Objects.requireNonNull(area, "확인한 거주 허용 범위가 필요합니다.");
            districts = Set.copyOf(districts);
            if (area == Area.SEOUL_DISTRICTS && districts.isEmpty()) {
                throw new IllegalArgumentException("자치구 조건에는 허용 자치구가 하나 이상 필요합니다.");
            }
            if (area != Area.SEOUL_DISTRICTS && !districts.isEmpty()) {
                throw new IllegalArgumentException("전국·서울 전체 조건에는 자치구를 지정할 수 없습니다.");
            }
            referenceDate = Objects.requireNonNull(referenceDate, "확인한 거주 기준일이 필요합니다.");
            evidence = Objects.requireNonNull(evidence, "거주 조건의 원문 근거가 필요합니다.");
        }

        @Override
        public String appliedCondition() {
            String location = switch (area) {
                case NATIONWIDE -> "전국";
                case SEOUL -> "서울특별시";
                case SEOUL_DISTRICTS -> "서울특별시 " + districts.stream().sorted()
                        .map(SeoulDistrict::label).collect(Collectors.joining(", ")) + " 중 한 곳";
            };
            return referenceDate + " 기준 주민등록상 " + location + " 거주";
        }
    }

    record Unresolved(
            String conditionId,
            String appliedCondition,
            Optional<LocalDate> referenceDate,
            String reason,
            SourceEvidence evidence
    ) implements ResidenceCondition {

        public Unresolved {
            if (conditionId == null || conditionId.isBlank()
                    || appliedCondition == null || appliedCondition.isBlank()
                    || reason == null || reason.isBlank()) {
                throw new IllegalArgumentException("거주 조건 식별자, 확인할 조건과 미해석 이유가 필요합니다.");
            }
            referenceDate = Objects.requireNonNull(referenceDate, "거주 기준일의 존재 여부가 필요합니다.");
            evidence = Objects.requireNonNull(evidence, "미해석 거주 조건의 원문 근거가 필요합니다.");
        }
    }

    enum Area {
        NATIONWIDE,
        SEOUL,
        SEOUL_DISTRICTS
    }
}
