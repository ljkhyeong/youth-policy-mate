package kr.youthpolicymate.ingestion;

import kr.youthpolicymate.ingestion.OntongSweepStore.Step;
import org.springframework.stereotype.Service;
import java.util.UUID;

@Service
public class OntongSweepRunner {
    private final OntongSweepStore sweeps;
    private final OntongCollectionStore pages;
    private final OntongCollectionService collection;
    OntongSweepRunner(OntongSweepStore sweeps, OntongCollectionStore pages, OntongCollectionService collection) {
        this.sweeps = sweeps; this.pages = pages; this.collection = collection;
    }

    /** 배정한 페이지 하나를 트랜잭션 밖에서 처리하고 결과를 범위 진행 위치에 기록한다. */
    public Step tick(UUID id) {
        OntongSweepStore.Work work;
        try { work = sweeps.claim(id); }
        catch (OntongApiClient.Failure failure) {
            return switch (failure.getMessage()) {
                case "LOCAL_REQUEST_INTERVAL" -> Step.LOCAL_REQUEST_INTERVAL;
                case "LOCAL_DAILY_LIMIT" -> Step.LOCAL_DAILY_LIMIT;
                default -> throw failure;
            };
        }
        if (work.kind() == Step.SKIP) { sweeps.finish(work, null); return Step.PROGRESSED; }
        if (work.kind() != Step.FETCH && work.kind() != Step.REPLAY) return work.kind();
        try {
            boolean completed;
            try {
                if (work.kind() == Step.FETCH) collection.receive(work.runId());
                collection.applyStored(work.runId());
                completed = true;
            } catch (RuntimeException exception) { completed = false; }
            var page = pages.pageStatus(work.runId());
            if (completed) {
                long expected = Math.min(10, Math.max(0, page.totalCount() - (page.number() - 1L) * 10));
                sweeps.finish(work, page.itemCount() == expected ? null : "UNEXPECTED_ITEM_COUNT");
            } else if (page.state().equals("READY") && !pages.pending(work.runId()).isEmpty()) {
                sweeps.finish(work, "ITEMS_REQUIRE_REPROCESSING");
            } else {
                sweeps.pause(work, page.failureCode() == null ? "COLLECTION_FAILED" : page.failureCode());
                return Step.PAUSED;
            }
            return Step.PROGRESSED;
        } catch (RuntimeException exception) {
            // 공급자 예외 내용을 로그에 전파하지 않는다.
            sweeps.pause(work, "SWEEP_EXECUTION_FAILED"); return Step.PAUSED;
        }
    }
}
