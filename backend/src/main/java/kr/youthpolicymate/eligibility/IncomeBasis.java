package kr.youthpolicymate.eligibility;

import java.time.LocalDate;
import java.time.Year;
import java.util.Objects;
import java.util.Optional;

// 필요한 정의·기간·적용 기준을 확인한 내부 모델이다. 빈 선택값으로 미확인 기준을 숨기지 않는다.
public record IncomeBasis(
        String policyId,
        String policyRevision,
        String conditionId,
        Subject subject,
        String definition,
        Period period,
        Optional<Standard> standard,
        Optional<LocalDate> referenceDate
) {

    public IncomeBasis {
        if (policyId == null || policyId.isBlank()
                || policyRevision == null || policyRevision.isBlank()
                || conditionId == null || conditionId.isBlank()
                || definition == null || definition.isBlank()) {
            throw new IllegalArgumentException("정책 식별자·개정, 소득 조건 식별자와 소득 정의가 필요합니다.");
        }
        subject = Objects.requireNonNull(subject, "소득의 개인·가구 기준이 필요합니다.");
        period = Objects.requireNonNull(period, "소득 대상 기간과 산정 방식이 필요합니다.");
        standard = Objects.requireNonNull(standard, "소득 적용 기준표의 존재 여부가 필요합니다.");
        referenceDate = Objects.requireNonNull(referenceDate, "소득 기준일의 존재 여부가 필요합니다.");
    }

    public String description() {
        return subject.description() + " · " + definition + " · 단위: 원 · 대상 기간: " + period.description()
                + standard.map(value -> " · 적용 기준: " + value.description()).orElse("")
                + referenceDate.map(date -> " · 기준일: " + date).orElse("");
    }

    public sealed interface Subject permits Personal, Household {
        String description();
    }

    public enum Personal implements Subject {
        INSTANCE;

        @Override
        public String description() {
            return "본인 소득";
        }
    }

    // 선택값 없음은 정책에서 요구하지 않음을 확인했다는 뜻이다. 필요한 정보가 미확인이면 비교를 보류한다.
    public record Household(
            String definition,
            Optional<Integer> memberCount,
            Optional<String> type,
            Optional<LocalDate> referenceDate
    ) implements Subject {
        public Household {
            if (definition == null || definition.isBlank()) {
                throw new IllegalArgumentException("정책이 정한 가구원 범위가 필요합니다.");
            }
            memberCount = Objects.requireNonNull(memberCount, "필요한 가구원 수의 존재 여부가 필요합니다.");
            type = Objects.requireNonNull(type, "필요한 가구 유형의 존재 여부가 필요합니다.");
            referenceDate = Objects.requireNonNull(referenceDate, "가구 산정 기준일의 존재 여부가 필요합니다.");
            if (memberCount.filter(count -> count < 1).isPresent() || type.filter(String::isBlank).isPresent()) {
                throw new IllegalArgumentException("가구원 수는 1명 이상이고 지정한 가구 유형은 비어 있지 않아야 합니다.");
            }
        }

        @Override
        public String description() {
            return "가구 소득: " + definition
                    + memberCount.map(count -> " · 가구원 " + count + "명").orElse("")
                    + type.map(value -> " · 가구 유형: " + value).orElse("")
                    + referenceDate.map(date -> " · 가구 산정일: " + date).orElse("");
        }
    }

    public record Period(LocalDate startInclusive, LocalDate endInclusive, String calculation) {
        public Period {
            startInclusive = Objects.requireNonNull(startInclusive, "소득 대상 기간 시작일이 필요합니다.");
            endInclusive = Objects.requireNonNull(endInclusive, "소득 대상 기간 종료일이 필요합니다.");
            if (startInclusive.isAfter(endInclusive) || calculation == null || calculation.isBlank()) {
                throw new IllegalArgumentException("소득 대상 기간의 순서와 합계·평균 등 산정 방식이 필요합니다.");
            }
        }

        public String description() {
            return startInclusive + "~" + endInclusive + " (양 끝 날짜 포함, " + calculation + ")";
        }
    }

    // 이미 확인한 금액의 적용 기준을 보존한다. 기준표 조회나 소득 산정을 수행하지 않는다.
    public record Standard(String name, Year year, String revision) {
        public Standard {
            if (name == null || name.isBlank() || revision == null || revision.isBlank()) {
                throw new IllegalArgumentException("적용 기준표 이름과 개정이 필요합니다.");
            }
            year = Objects.requireNonNull(year, "적용 기준표 연도가 필요합니다.");
        }

        public String description() {
            return name + " · " + year + "년 · 개정 " + revision;
        }
    }
}
