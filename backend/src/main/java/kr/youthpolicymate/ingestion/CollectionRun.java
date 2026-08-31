package kr.youthpolicymate.ingestion;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import kr.youthpolicymate.ingestion.CollectionAttempt.EndOfRange;
import kr.youthpolicymate.ingestion.CollectionAttempt.Failed;
import kr.youthpolicymate.ingestion.CollectionAttempt.Id;
import kr.youthpolicymate.ingestion.CollectionAttempt.Interrupted;
import kr.youthpolicymate.ingestion.CollectionAttempt.ItemProcessed;
import kr.youthpolicymate.ingestion.CollectionAttempt.NextPage;
import kr.youthpolicymate.ingestion.CollectionAttempt.Outcome;
import kr.youthpolicymate.ingestion.CollectionAttempt.PageFetched;
import kr.youthpolicymate.ingestion.CollectionAttempt.Result;
import kr.youthpolicymate.ingestion.CollectionPosition.Item;
import kr.youthpolicymate.ingestion.CollectionPosition.Page;

public final class CollectionRun {
    private final String runId;
    private final Instant startedAt;
    private final Map<CollectionPosition, Progress> work;
    private final boolean endConfirmed;
    private final boolean paused;
    private final Optional<Instant> lastInterruptedAt;

    private CollectionRun(String runId, Instant startedAt, Map<CollectionPosition, Progress> work,
                          boolean endConfirmed, boolean paused, Optional<Instant> lastInterruptedAt) {
        this.runId = runId;
        this.startedAt = startedAt;
        this.work = Collections.unmodifiableMap(new LinkedHashMap<>(work));
        this.endConfirmed = endConfirmed;
        this.paused = paused;
        this.lastInterruptedAt = lastInterruptedAt;
    }

    public static CollectionRun start(String runId, Page firstPage, Instant startedAt) {
        if (runId == null || runId.isBlank()) {
            throw new IllegalArgumentException("실행 ID가 필요합니다.");
        }
        Objects.requireNonNull(firstPage, "첫 페이지 위치가 필요합니다.");
        Objects.requireNonNull(startedAt, "실행 시작 시각이 필요합니다.");
        return new CollectionRun(runId, startedAt, Map.of(firstPage, new Progress(startedAt, List.of())),
                false, false, Optional.empty());
    }

    public String runId() { return runId; }
    public Instant startedAt() { return startedAt; }
    public boolean endConfirmed() { return endConfirmed; }
    public Optional<Instant> lastInterruptedAt() { return lastInterruptedAt; }
    public List<CollectionPosition> positions() { return List.copyOf(work.keySet()); }

    public List<CollectionAttempt> attempts(CollectionPosition position) {
        return progress(position).attempts();
    }

    public List<CollectionPosition> resumeTargets() {
        return work.entrySet().stream().filter(entry -> entry.getValue().canBegin())
                .map(Map.Entry::getKey).toList();
    }

    public List<CollectionPosition> reviewTargets() {
        return work.entrySet().stream()
                .filter(entry -> entry.getValue().outcome().orElse(null) == ItemProcessed.REVIEW_REQUIRED)
                .map(Map.Entry::getKey).toList();
    }

    public Started begin(CollectionPosition position, Instant at) {
        Objects.requireNonNull(at, "시도 시작 시각이 필요합니다.");
        var progress = progress(position);
        if (paused || !progress.canBegin()) {
            throw new IllegalStateException("현재 작업 위치에서 새 시도를 시작할 수 없습니다.");
        }
        var previousEnd = progress.latest().flatMap(CollectionAttempt::result).map(Result::finishedAt);
        if (at.isBefore(progress.availableAt()) || previousEnd.filter(at::isBefore).isPresent()
                || lastInterruptedAt.filter(at::isBefore).isPresent()) {
            throw new IllegalArgumentException("작업 등록·이전 시도 종료·중단보다 먼저 시작할 수 없습니다.");
        }
        var id = new Id(runId, position, Math.addExact(progress.attempts().size(), 1));
        var attempts = new ArrayList<>(progress.attempts());
        attempts.add(new CollectionAttempt(id, at, Optional.empty()));
        var next = new LinkedHashMap<>(work);
        next.put(position, new Progress(progress.availableAt(), attempts));
        return new Started(id, copy(next, endConfirmed, paused, lastInterruptedAt));
    }

    public Updated record(Id id, Result result) {
        Objects.requireNonNull(id, "시도 식별값이 필요합니다.");
        Objects.requireNonNull(result, "시도 결과가 필요합니다.");
        if (!runId.equals(id.runId())) {
            throw new IllegalArgumentException("다른 실행의 결과를 기록할 수 없습니다.");
        }
        var progress = progress(id.position());
        var latest = progress.latest().orElseThrow(() -> new IllegalArgumentException("시작하지 않은 작업입니다."));
        if (id.number() > latest.id().number()) {
            throw new IllegalArgumentException("발급하지 않은 시도 번호입니다.");
        }
        if (id.number() < latest.id().number() || progress.outcome().orElse(null) == Interrupted.PAUSED) {
            return new Updated(Decision.STALE, this);
        }
        if (latest.result().isPresent()) {
            return new Updated(latest.result().get().equals(result) ? Decision.REPLAYED : Decision.CONFLICT, this);
        }
        validateOutcome(id.position(), result.outcome());
        var completed = new CollectionAttempt(id, latest.startedAt(), Optional.of(result));
        var next = new LinkedHashMap<>(work);
        var attempts = new ArrayList<>(progress.attempts());
        attempts.set(attempts.size() - 1, completed);
        next.put(id.position(), new Progress(progress.availableAt(), attempts));
        boolean reachedEnd = endConfirmed;
        if (result.outcome() instanceof PageFetched page) {
            var position = (Page) id.position();
            if (page.continuation() instanceof NextPage continuation && work.containsKey(continuation.page())) {
                throw new IllegalArgumentException("이미 등록된 페이지로 다시 이동할 수 없습니다.");
            }
            for (String item : page.itemReferences()) {
                next.put(new Item(position.reference(), item), new Progress(result.finishedAt(), List.of()));
            }
            if (page.continuation() instanceof NextPage continuation) {
                next.put(continuation.page(), new Progress(result.finishedAt(), List.of()));
            } else {
                reachedEnd = page.continuation() == EndOfRange.CONFIRMED;
            }
        }
        return new Updated(Decision.RECORDED, copy(next, reachedEnd, paused, lastInterruptedAt));
    }

    public CollectionRun pause(Instant at) {
        Objects.requireNonNull(at, "중단 시각이 필요합니다.");
        if (paused || isCompleted()) {
            return this;
        }
        if (at.isBefore(startedAt) || lastInterruptedAt.filter(at::isBefore).isPresent()
                || work.values().stream().flatMap(progress -> progress.latest().stream())
                .anyMatch(attempt -> at.isBefore(attempt.result().map(Result::finishedAt).orElse(attempt.startedAt())))) {
            throw new IllegalArgumentException("이미 기록한 작업보다 먼저 중단할 수 없습니다.");
        }
        var next = new LinkedHashMap<>(work);
        work.forEach((position, progress) -> {
            var latest = progress.latest();
            if (latest.isPresent() && latest.get().result().isEmpty()) {
                var attempts = new ArrayList<>(progress.attempts());
                attempts.set(attempts.size() - 1, new CollectionAttempt(latest.get().id(), latest.get().startedAt(),
                        Optional.of(new Result(Interrupted.PAUSED, at))));
                next.put(position, new Progress(progress.availableAt(), attempts));
            }
        });
        return copy(next, endConfirmed, true, Optional.of(at));
    }

    public CollectionRun resume() {
        return paused ? copy(work, endConfirmed, false, lastInterruptedAt) : this;
    }

    public Status status() {
        if (paused) return Status.PAUSED;
        if (work.values().stream().anyMatch(Progress::retryRequired)) return Status.RETRY_REQUIRED;
        if (!endConfirmed || work.values().stream().anyMatch(progress -> progress.outcome().isEmpty())) {
            return Status.RUNNING;
        }
        return reviewTargets().isEmpty() ? Status.COMPLETED : Status.COMPLETED_WITH_REVIEW;
    }

    public Optional<Instant> finishedAt() {
        if (!isCompleted()) return Optional.empty();
        return work.values().stream().flatMap(progress -> progress.latest().stream())
                .flatMap(attempt -> attempt.result().stream()).map(Result::finishedAt).max(Instant::compareTo);
    }

    private boolean isCompleted() {
        var status = status();
        return status == Status.COMPLETED || status == Status.COMPLETED_WITH_REVIEW;
    }

    private Progress progress(CollectionPosition position) {
        var progress = work.get(position);
        if (progress == null) throw new IllegalArgumentException("등록하지 않은 작업 위치입니다.");
        return progress;
    }

    private static void validateOutcome(CollectionPosition position, Outcome outcome) {
        boolean valid = switch (outcome) {
            case PageFetched ignored -> position instanceof Page;
            case ItemProcessed ignored -> position instanceof Item;
            case Failed failure -> switch (failure.code()) {
                case FETCH_FAILED -> true;
                case UNREADABLE_PAGE -> position instanceof Page;
                case ITEM_PROCESSING_FAILED -> position instanceof Item;
            };
            case Interrupted ignored -> false;
        };
        if (!valid) {
            throw new IllegalArgumentException("작업 위치에 맞지 않는 결과입니다. 중단은 실행 중단 기능으로 기록합니다.");
        }
    }

    private CollectionRun copy(Map<CollectionPosition, Progress> next, boolean reachedEnd,
                               boolean isPaused, Optional<Instant> interruptedAt) {
        return new CollectionRun(runId, startedAt, next, reachedEnd, isPaused, interruptedAt);
    }

    private record Progress(Instant availableAt, List<CollectionAttempt> attempts) {
        private Progress {
            attempts = List.copyOf(attempts);
        }

        private Optional<CollectionAttempt> latest() {
            return attempts.isEmpty() ? Optional.empty() : Optional.of(attempts.getLast());
        }

        private Optional<Outcome> outcome() {
            return latest().flatMap(CollectionAttempt::result).map(Result::outcome);
        }

        private boolean retryRequired() {
            return outcome().filter(value -> value instanceof Failed || value == Interrupted.PAUSED).isPresent();
        }

        private boolean canBegin() {
            return attempts.isEmpty() || retryRequired();
        }
    }

    public enum Status { RUNNING, PAUSED, RETRY_REQUIRED, COMPLETED_WITH_REVIEW, COMPLETED }
    public enum Decision { RECORDED, REPLAYED, STALE, CONFLICT }
    public record Started(Id attemptId, CollectionRun state) {}
    public record Updated(Decision decision, CollectionRun state) {}
}
