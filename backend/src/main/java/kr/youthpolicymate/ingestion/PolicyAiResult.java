package kr.youthpolicymate.ingestion;

import kr.youthpolicymate.policy.PolicyRevisionState.AppliedRevision;

import java.time.Instant;
import java.util.Objects;

// 공급자 응답 DTO가 아니다. 호출 측이 분류한 결과와 참조만 받는다.
public record PolicyAiResult(Request request, Instant recordedAt, Outcome outcome) {
    public PolicyAiResult {
        Objects.requireNonNull(request, "AI 예정 요청이 필요합니다.");
        Objects.requireNonNull(recordedAt, "결과 확인 시각이 필요합니다.");
        Objects.requireNonNull(outcome, "AI 처리 결과가 필요합니다.");
        if (recordedAt.isBefore(request.preparedAt())) {
            throw new IllegalArgumentException("결과 확인은 요청 준비보다 빠를 수 없습니다.");
        }
    }

    public record Request(AppliedRevision sourceRevision, Kind kind, String generationVersion,
                          long sequence, Instant preparedAt) {
        public Request {
            Objects.requireNonNull(sourceRevision, "요청 근거인 정책 적용 개정이 필요합니다.");
            Objects.requireNonNull(kind, "요약·조건 추출 구분이 필요합니다.");
            requireText(generationVersion, "AI 생성 방식 버전이 필요합니다.");
            if (sequence < 1) throw new IllegalArgumentException("AI 요청 순번은 1 이상이어야 합니다.");
            Objects.requireNonNull(preparedAt, "요청 준비 시각이 필요합니다.");
        }

        public String policyId() { return sourceRevision.observation().policyId(); }
    }

    public enum Kind { SUMMARY, CONDITION_EXTRACTION }

    public sealed interface Outcome permits Generated, Unavailable {}

    // 후보 본문을 보관하거나 자격 규칙으로 변환하지 않는다.
    public record Generated(String candidateId, String bodySha256) implements Outcome {
        public Generated {
            requireText(candidateId, "AI 후보의 내부 식별자가 필요합니다.");
            if (bodySha256 == null || !bodySha256.matches("[0-9a-f]{64}")) {
                throw new IllegalArgumentException("후보 본문 SHA-256은 소문자 16진수 64자리여야 합니다.");
            }
        }
    }

    public record Unavailable(UnavailableReason reason) implements Outcome {
        public Unavailable {
            Objects.requireNonNull(reason, "AI 후보를 받지 못한 사유가 필요합니다.");
        }
    }

    public enum UnavailableReason { REQUEST_FAILED, INVALID_OUTPUT, BUDGET_LIMIT }

    private static void requireText(String value, String message) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(message);
    }
}
