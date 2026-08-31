package kr.youthpolicymate.policy;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

// 원천 DTO가 아니다. 수집 경계에서 식별·확인한 한 정책의 결과를 받는다.
public record PolicyObservation(
        String policyId,
        long collectionSequence,
        Instant observedAt,
        Outcome outcome
) {
    public PolicyObservation {
        requireText(policyId, "내부 정책 식별자가 필요합니다.");
        if (collectionSequence < 1) throw new IllegalArgumentException("내부 수집 순번은 1 이상이어야 합니다.");
        observedAt = Objects.requireNonNull(observedAt, "수집 결과 확인 시각이 필요합니다.");
        outcome = Objects.requireNonNull(outcome, "수집 결과가 필요합니다.");
    }

    public sealed interface Outcome permits Readable, Failed {}

    // 표시 가능 여부이며, 자격 조건이 모두 해석되었다는 뜻은 아니다.
    public record Readable(SnapshotReference snapshot, ContentFingerprint content) implements Outcome {
        public Readable {
            snapshot = Objects.requireNonNull(snapshot, "원본 참조가 필요합니다.");
            content = Objects.requireNonNull(content, "비교 내용 식별값이 필요합니다.");
        }
    }

    public record Failed(FailureReason reason, Optional<SnapshotReference> snapshot) implements Outcome {
        public Failed {
            reason = Objects.requireNonNull(reason, "수집 실패 분류가 필요합니다.");
            snapshot = Objects.requireNonNull(snapshot, "확보한 원본 참조의 존재 여부가 필요합니다.");
        }
    }

    public enum FailureReason { FETCH_FAILED, UNREADABLE_CONTENT }

    // 원문 자체나 키가 포함된 요청 URL을 담지 않는다. 저장 구현은 별도다.
    public record SnapshotReference(String sourceName, String snapshotId, String bodySha256) {
        public SnapshotReference {
            requireText(sourceName, "원천명이 필요합니다.");
            requireText(snapshotId, "원본 스냅샷 식별자가 필요합니다.");
            requireSha256(bodySha256);
        }
    }

    public record ContentFingerprint(String comparisonVersion, String sha256) {
        public ContentFingerprint {
            requireText(comparisonVersion, "내용 비교 방식 버전이 필요합니다.");
            requireSha256(sha256);
        }
    }

    private static void requireText(String value, String message) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(message);
    }

    private static void requireSha256(String value) {
        if (value == null || !value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("SHA-256은 소문자 16진수 64자리여야 합니다.");
        }
    }
}
