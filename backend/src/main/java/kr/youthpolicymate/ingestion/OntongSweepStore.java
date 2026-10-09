package kr.youthpolicymate.ingestion;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class OntongSweepStore {
    private final JdbcClient jdbc;
    private final OntongCollectionStore pages;
    private final OntongProperties properties;
    private final Clock clock;
    OntongSweepStore(JdbcClient jdbc, OntongCollectionStore pages, OntongProperties properties, Clock clock) {
        this.jdbc = jdbc; this.pages = pages; this.properties = properties; this.clock = clock;
    }

    @Transactional
    public UUID create(int first, int last) {
        properties.requireLimits(); validateRange(first, last); lockGate();
        if (open().isPresent()) throw new OntongApiClient.Failure("SWEEP_ALREADY_OPEN");
        return insert(first, last, "MANUAL");
    }

    /** 범위·주기·한도는 OntongSweepScheduler 생성 시 검증한다. */
    @Transactional
    public Optional<UUID> scheduled(int first, int last, Duration interval) {
        lockGate();
        var current = open();
        if (current.isPresent()) return current.filter(value -> value.origin().equals("SCHEDULED")).map(Sweep::id);
        var previous = jdbc.sql("SELECT max(completed_at) FROM ontong_collection_sweeps WHERE origin = 'SCHEDULED'")
                .query(OffsetDateTime.class).optional();
        if (previous.isPresent() && clock.instant().isBefore(previous.get().toInstant().plus(interval))) return Optional.empty();
        return Optional.of(insert(first, last, "SCHEDULED"));
    }

    /** 요청 기록과 페이지 배정을 먼저 커밋한다. 기존 REQUESTED에는 다시 FETCH를 반환하지 않는다. */
    @Transactional
    public Work claim(UUID id) {
        lockGate();
        var sweep = find(id);
        // DB CHECK가 상태를 다섯 값으로 제한하고 ACTIVE는 여기서 걸러진다.
        if (!sweep.state().equals("ACTIVE")) return new Work(Step.valueOf(sweep.state()), id, null, sweep.nextPage());
        var assigned = jdbc.sql("SELECT run_id, outcome FROM ontong_collection_sweep_pages WHERE sweep_id = :id AND page_number = :page")
                .param("id", id).param("page", sweep.nextPage()).query(Assignment.class).optional();
        if (assigned.isPresent()) {
            var runId = assigned.get().runId();
            if (assigned.get().outcome().equals("COMPLETED")) return new Work(Step.SKIP, id, runId, sweep.nextPage());
            var page = pages.pageStatus(runId);
            if (page.responseStored()) return new Work(Step.REPLAY, id, runId, sweep.nextPage());
            if (page.state().equals("FETCH_FAILED")) {
                var paused = new Work(Step.PAUSED, id, runId, sweep.nextPage());
                pause(paused, page.failureCode()); return paused;
            }
            return new Work(Step.WAITING_RESPONSE, id, runId, sweep.nextPage());
        }
        properties.requireLimits();
        var run = UUID.randomUUID();
        pages.begin(run, sweep.nextPage());
        jdbc.sql("INSERT INTO ontong_collection_sweep_pages(sweep_id, page_number, run_id, outcome) VALUES (:id, :page, :run, 'REQUESTED')")
                .param("id", id).param("page", sweep.nextPage()).param("run", run).update();
        return new Work(Step.FETCH, id, run, sweep.nextPage());
    }

    /** 실패 코드가 없으면 페이지를 COMPLETED, 있으면 PARTIAL로 기록한다. */
    @Transactional
    public void finish(Work work, String failureCode) {
        lockGate();
        var sweep = find(work.sweepId());
        if (!current(sweep, work)) return;
        updatePage(work, failureCode == null ? "COMPLETED" : "PARTIAL", failureCode);
        boolean last = work.page() == sweep.lastPage();
        boolean partial = jdbc.sql("SELECT EXISTS(SELECT 1 FROM ontong_collection_sweep_pages WHERE sweep_id = :id AND outcome <> 'COMPLETED')")
                .param("id", work.sweepId()).query(Boolean.class).single();
        jdbc.sql("""
                UPDATE ontong_collection_sweeps SET next_page = :next, state = :state,
                    completed_at = :completed, failure_code = :code,
                    last_successful_at = CASE WHEN :success THEN :now ELSE last_successful_at END WHERE id = :id
                """).param("id", work.sweepId()).param("next", work.page() + 1)
                .param("state", last ? partial ? "PARTIAL" : "COMPLETED" : "ACTIVE")
                .param("now", now()).param("completed", last ? now() : null)
                .param("code", last && partial ? "PAGES_REQUIRE_REVIEW" : null).param("success", failureCode == null).update();
    }

    @Transactional
    public void pause(Work work, String code) {
        lockGate();
        var sweep = find(work.sweepId());
        if (!current(sweep, work)) return;
        updatePage(work, "FAILED", code);
        jdbc.sql("UPDATE ontong_collection_sweeps SET state = 'PAUSED', failure_code = :code WHERE id = :id")
                .param("id", work.sweepId()).param("code", code).update();
    }

    @Transactional
    public void resume(UUID id) {
        lockGate();
        var sweep = find(id);
        if (sweep.state().equals("ACTIVE")) return;
        if (!List.of("PAUSED", "PARTIAL").contains(sweep.state())) throw new OntongApiClient.Failure("SWEEP_NOT_RESUMABLE");
        if (open().filter(value -> !value.id().equals(id)).isPresent()) throw new OntongApiClient.Failure("SWEEP_ALREADY_OPEN");
        int page = jdbc.sql("SELECT min(page_number) FROM ontong_collection_sweep_pages WHERE sweep_id = :id AND outcome <> 'COMPLETED'")
                .param("id", id).query(Integer.class).single();
        boolean stored = jdbc.sql("""
                SELECT p.raw_body IS NOT NULL FROM ontong_collection_sweep_pages s
                JOIN ontong_collection_pages p ON p.run_id = s.run_id WHERE s.sweep_id = :id AND s.page_number = :page
                """).param("id", id).param("page", page).query(Boolean.class).single();
        if (!stored) throw new OntongApiClient.Failure("RESPONSE_UNKNOWN_REQUIRES_REVIEW");
        jdbc.sql("UPDATE ontong_collection_sweeps SET state = 'ACTIVE', next_page = :page, completed_at = NULL, failure_code = NULL WHERE id = :id")
                .param("id", id).param("page", page).update();
    }

    @Transactional
    public void abandon(UUID id) {
        lockGate(); find(id);
        jdbc.sql("UPDATE ontong_collection_sweeps SET state = 'ABANDONED', completed_at = :now, failure_code = 'OPERATOR_ABANDONED' WHERE id = :id AND state IN ('ACTIVE','PAUSED','PARTIAL')")
                .param("id", id).param("now", now()).update();
    }

    public Sweep find(UUID id) {
        return jdbc.sql("SELECT id, origin, last_page, next_page, state FROM ontong_collection_sweeps WHERE id = :id")
                .param("id", id).query(Sweep.class).optional()
                .orElseThrow(() -> new OntongApiClient.Failure("SWEEP_NOT_FOUND"));
    }
    public List<String> status(UUID id) {
        return jdbc.sql("""
                SELECT s.*, count(p.*) FILTER (WHERE p.outcome = 'COMPLETED') AS done,
                    count(p.*) FILTER (WHERE p.outcome IN ('PARTIAL','FAILED')) AS failed
                FROM ontong_collection_sweeps s LEFT JOIN ontong_collection_sweep_pages p ON s.id = p.sweep_id
                WHERE (:all OR s.id = :id) GROUP BY s.id ORDER BY s.started_at DESC LIMIT 10
                """).param("all", id == null).param("id", id == null ? new UUID(0, 0) : id)
                .query((rs, row) -> "범위 실행 " + rs.getString("id") + " | " + rs.getString("origin") + " | " + rs.getString("state")
                        + " | 범위 " + rs.getInt("first_page") + "~" + rs.getInt("last_page") + " | 다음 위치 " + rs.getInt("next_page")
                        + " | 완료 페이지 " + rs.getLong("done") + " | 확인 필요 " + rs.getLong("failed")
                        + " | 마지막 정상 처리 " + rs.getObject("last_successful_at") + " | 오류 " + rs.getString("failure_code")).list();
    }
    public List<String> pageStatus(UUID id) {
        return jdbc.sql("SELECT page_number, run_id, outcome, failure_code FROM ontong_collection_sweep_pages WHERE sweep_id = :id ORDER BY page_number")
                .param("id", id).query((rs, row) -> "페이지 " + rs.getInt(1) + " | 실행 " + rs.getString(2) + " | " + rs.getString(3) + " | 오류 " + rs.getString(4)).list();
    }
    private Optional<Sweep> open() {
        return jdbc.sql("SELECT id FROM ontong_collection_sweeps WHERE state IN ('ACTIVE','PAUSED')")
                .query(UUID.class).optional().map(this::find);
    }
    private UUID insert(int first, int last, String origin) {
        var id = UUID.randomUUID();
        jdbc.sql("INSERT INTO ontong_collection_sweeps(id, origin, first_page, last_page, next_page, state, started_at) VALUES (:id, :origin, :first, :last, :first, 'ACTIVE', :now)")
                .param("id", id).param("origin", origin).param("first", first).param("last", last).param("now", now()).update();
        return id;
    }
    private void updatePage(Work work, String outcome, String code) {
        int updated = jdbc.sql("UPDATE ontong_collection_sweep_pages SET outcome = :outcome, failure_code = :code WHERE sweep_id = :id AND page_number = :page AND run_id = :run")
                .param("id", work.sweepId()).param("page", work.page()).param("run", work.runId()).param("outcome", outcome).param("code", code).update();
        if (updated != 1) throw new OntongApiClient.Failure("SWEEP_WORK_CHANGED");
    }
    private boolean current(Sweep sweep, Work work) { return sweep.state().equals("ACTIVE") && sweep.nextPage() == work.page(); }
    /** 요청 배정·발송 시작과 같은 잠금으로 범위 상태 변경을 직렬화한다. */
    private void lockGate() { OntongCollectionStore.lockRequests(jdbc); }
    private OffsetDateTime now() { return clock.instant().atOffset(ZoneOffset.UTC); }
    static void validateRange(int first, int last) { if (first < 1 || last < first || last > 1000) throw new IllegalArgumentException("수집 페이지 범위는 1~1000 안에서 지정해주세요."); }
    public record Sweep(UUID id, String origin, int lastPage, int nextPage, String state) {}
    /** claim이 지시한 처리와 tick 결과. 이름을 CLI·로그 출력에 그대로 쓴다. */
    public enum Step { FETCH, REPLAY, SKIP, WAITING_RESPONSE, PAUSED, COMPLETED, PARTIAL, ABANDONED, PROGRESSED, LOCAL_REQUEST_INTERVAL, LOCAL_DAILY_LIMIT }
    public record Work(Step kind, UUID sweepId, UUID runId, int page) {}
    private record Assignment(UUID runId, String outcome) {}
}
