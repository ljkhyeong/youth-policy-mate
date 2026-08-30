package kr.youthpolicymate.policy;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Objects;
import java.util.Optional;

public record RecruitmentSchedule(
        String policyId,
        String policyRevision,
        ApplicationPeriod applicationPeriod,
        String sourceReference,
        String sourceLocation,
        Optional<String> sourceExcerpt
) {
    public RecruitmentSchedule {
        if (policyId == null || policyId.isBlank() || policyRevision == null || policyRevision.isBlank()) {
            throw new IllegalArgumentException("모집 안내의 정책 식별자와 적용 개정이 필요합니다.");
        }
        applicationPeriod = Objects.requireNonNull(applicationPeriod, "확인한 신청기간 또는 미확인 이유가 필요합니다.");
        if (sourceReference == null || sourceReference.isBlank() || sourceLocation == null || sourceLocation.isBlank()) {
            throw new IllegalArgumentException("신청기간의 원문 참조와 확인한 위치가 필요합니다.");
        }
        sourceExcerpt = Objects.requireNonNull(sourceExcerpt, "원문 발췌의 존재 여부가 필요합니다.");
    }

    // 달력 계산용 날짜이며 원본의 날짜형·시각형 구분은 applicationPeriod에 보존한다.
    public Optional<LocalDate> confirmedDeadlineOnSeoul() {
        return switch (applicationPeriod) {
            case ApplicationPeriod.Dates dates -> Optional.of(dates.endsOnInclusive());
            case ApplicationPeriod.Times times -> Optional.of(
                    times.closesAtExclusive().withZoneSameInstant(ZoneId.of("Asia/Seoul")).toLocalDate());
            case ApplicationPeriod.Rolling ignored -> Optional.empty();
            case ApplicationPeriod.UntilExhausted ignored -> Optional.empty();
            case ApplicationPeriod.Closed ignored -> Optional.empty();
            case ApplicationPeriod.Unresolved ignored -> Optional.empty();
        };
    }
}
