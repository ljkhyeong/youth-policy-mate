package kr.youthpolicymate.ingestion;

import kr.youthpolicymate.policy.PolicyObservation.SnapshotReference;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record CollectionAttempt(Id id, Instant startedAt, Optional<Result> result) {
    public CollectionAttempt {
        Objects.requireNonNull(id, "시도 식별값이 필요합니다.");
        Objects.requireNonNull(startedAt, "시도 시작 시각이 필요합니다.");
        Objects.requireNonNull(result, "시도 결과 여부가 필요합니다.");
        if (result.isPresent() && result.get().finishedAt().isBefore(startedAt)) {
            throw new IllegalArgumentException("시도 종료는 시작보다 빠를 수 없습니다.");
        }
    }

    public record Id(String runId, CollectionPosition position, int number) {
        public Id {
            if (runId == null || runId.isBlank() || number < 1) {
                throw new IllegalArgumentException("실행 ID와 양수 시도 번호가 필요합니다.");
            }
            Objects.requireNonNull(position, "작업 위치가 필요합니다.");
        }
    }

    public record Result(Outcome outcome, Instant finishedAt) {
        public Result {
            Objects.requireNonNull(outcome, "종료 결과가 필요합니다.");
            Objects.requireNonNull(finishedAt, "종료 시각이 필요합니다.");
        }
    }

    public sealed interface Outcome permits PageFetched, ItemProcessed, Failed, Interrupted {}

    public record PageFetched(SnapshotReference snapshot, List<String> itemReferences,
                              Continuation continuation) implements Outcome {
        public PageFetched {
            Objects.requireNonNull(snapshot, "페이지 원본 참조가 필요합니다.");
            itemReferences = List.copyOf(itemReferences);
            if (itemReferences.stream().anyMatch(String::isBlank)
                    || new HashSet<>(itemReferences).size() != itemReferences.size()) {
                throw new IllegalArgumentException("페이지 안의 항목 참조는 비어 있거나 중복될 수 없습니다.");
            }
            Objects.requireNonNull(continuation, "다음 페이지 또는 종료 확인이 필요합니다.");
        }
    }

    public sealed interface Continuation permits NextPage, EndOfRange {}

    public record NextPage(CollectionPosition.Page page) implements Continuation {
        public NextPage {
            Objects.requireNonNull(page, "다음 페이지 위치가 필요합니다.");
        }
    }

    public enum EndOfRange implements Continuation { CONFIRMED }

    public enum ItemProcessed implements Outcome { COMPLETED, REVIEW_REQUIRED }

    public record Failed(FailureCode code, Optional<SnapshotReference> snapshot) implements Outcome {
        public Failed {
            Objects.requireNonNull(code, "실패 코드가 필요합니다.");
            Objects.requireNonNull(snapshot, "실패 시 원본 확보 여부가 필요합니다.");
        }
    }

    public enum FailureCode { FETCH_FAILED, UNREADABLE_PAGE, ITEM_PROCESSING_FAILED }

    public enum Interrupted implements Outcome { PAUSED }
}
