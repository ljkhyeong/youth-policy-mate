package kr.youthpolicymate.eligibility;

import java.util.List;
import java.util.Objects;

public record PolicyReview(Completion completion, List<PendingIssue> pendingIssues) {

    public PolicyReview {
        completion = Objects.requireNonNull(completion, "정책 검토 상태가 필요합니다.");
        pendingIssues = List.copyOf(pendingIssues);
        if ((completion == Completion.COMPLETE) != pendingIssues.isEmpty()) {
            throw new IllegalArgumentException("검토 미완료에는 확인할 항목이 필요하고, 검토 완료에는 남은 항목이 없어야 합니다.");
        }
    }

    public static PolicyReview complete() {
        return new PolicyReview(Completion.COMPLETE, List.of());
    }

    public static PolicyReview incomplete(List<PendingIssue> pendingIssues) {
        return new PolicyReview(Completion.INCOMPLETE, pendingIssues);
    }

    public enum Completion {
        COMPLETE,
        INCOMPLETE
    }

    public record PendingIssue(String explanation, SourceEvidence evidence) {

        public PendingIssue {
            if (explanation == null || explanation.isBlank()) {
                throw new IllegalArgumentException("추가로 확인할 정책 조건이나 예외를 설명해야 합니다.");
            }
            evidence = Objects.requireNonNull(evidence, "추가 확인의 원문 근거가 필요합니다.");
        }
    }
}
