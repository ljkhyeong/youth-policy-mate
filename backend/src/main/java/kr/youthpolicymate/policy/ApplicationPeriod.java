package kr.youthpolicymate.policy;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.Objects;

public sealed interface ApplicationPeriod {

    record Dates(LocalDate startsOnInclusive, LocalDate endsOnInclusive) implements ApplicationPeriod {
        public Dates {
            Objects.requireNonNull(startsOnInclusive, "신청 시작 날짜가 필요합니다.");
            Objects.requireNonNull(endsOnInclusive, "신청 종료 날짜가 필요합니다.");
            if (endsOnInclusive.isBefore(startsOnInclusive)) {
                throw new IllegalArgumentException("신청 종료 날짜가 시작 날짜보다 빠를 수 없습니다.");
            }
        }
    }

    // 원문의 시간대와 접수 종료 경계를 확인한 뒤 전달한다.
    record Times(ZonedDateTime opensAtInclusive, ZonedDateTime closesAtExclusive) implements ApplicationPeriod {
        public Times {
            Objects.requireNonNull(opensAtInclusive, "시간대를 확인한 신청 시작 시각이 필요합니다.");
            Objects.requireNonNull(closesAtExclusive, "시간대를 확인한 신청 마감 시각이 필요합니다.");
            if (!opensAtInclusive.toInstant().isBefore(closesAtExclusive.toInstant())) {
                throw new IllegalArgumentException("신청 마감 시각은 시작 시각보다 늦어야 합니다.");
            }
        }
    }

    record Rolling() implements ApplicationPeriod {
    }

    record UntilExhausted() implements ApplicationPeriod {
    }

    record Closed() implements ApplicationPeriod {
    }

    record Unresolved(String reason) implements ApplicationPeriod {
        public Unresolved {
            if (reason == null || reason.isBlank()) {
                throw new IllegalArgumentException("신청기간을 확인하지 못한 이유가 필요합니다.");
            }
        }
    }
}
