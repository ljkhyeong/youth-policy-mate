package kr.youthpolicymate.ingestion;

import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import java.util.UUID;

@Service
@Profile("!preview")
public class OntongSweepRunner {
    private final OntongSweepStore sweeps;
    private final OntongCollectionStore pages;
    private final JobOperator operator;
    private final Job job;
    public OntongSweepRunner(OntongSweepStore sweeps, OntongCollectionStore pages, JobOperator operator,
                             @Qualifier("limitedOntongCollection") Job job) {
        this.sweeps = sweeps; this.pages = pages; this.operator = operator; this.job = job;
    }

    public String tick(UUID id) {
        OntongSweepStore.Work work;
        try { work = sweeps.claim(id); }
        catch (OntongApiClient.Failure failure) {
            if (failure.getMessage().equals("LOCAL_REQUEST_INTERVAL") || failure.getMessage().equals("LOCAL_DAILY_LIMIT")) return failure.getMessage();
            throw failure;
        }
        if (work.kind().equals("SKIP")) { sweeps.finish(work, "COMPLETED", null); return "PROGRESSED"; }
        if (!work.kind().equals("FETCH") && !work.kind().equals("REPLAY")) return work.kind();
        try {
            var execution = operator.start(job, new JobParametersBuilder().addString("runId", work.runId().toString())
                    .addString("mode", work.kind().equals("FETCH") ? "receive" : "replay").addLong("page", (long) work.page())
                    .addString("invocation", UUID.randomUUID().toString()).toJobParameters());
            var page = pages.page(work.runId());
            if (execution.getStatus() == BatchStatus.COMPLETED) {
                long expected = Math.min(10, Math.max(0, page.totalCount() - (page.number() - 1L) * 10));
                boolean complete = page.itemCount() == expected;
                sweeps.finish(work, complete ? "COMPLETED" : "PARTIAL", complete ? null : "UNEXPECTED_ITEM_COUNT");
            } else if (page.state().equals("READY") && !pages.pending(work.runId()).isEmpty()) {
                sweeps.finish(work, "PARTIAL", "ITEMS_REQUIRE_REPROCESSING");
            } else {
                sweeps.pause(work, page.failureCode() == null ? "BATCH_EXECUTION_FAILED" : page.failureCode());
                return "PAUSED";
            }
            return "PROGRESSED";
        } catch (Exception exception) {
            // 공급자 예외나 배치 인자 전체를 로그에 전파하지 않는다.
            sweeps.pause(work, "SWEEP_EXECUTION_FAILED"); return "PAUSED";
        }
    }
}
