package kr.youthpolicymate.ingestion;

import org.springframework.context.annotation.Profile;
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
@Profile("!preview")
public class OntongSweepStore {
    private final JdbcClient jdbc;
    private final OntongCollectionStore pages;
    private final OntongRequestLimits limits;
    private final Clock clock;
    public OntongSweepStore(JdbcClient jdbc, OntongCollectionStore pages, OntongRequestLimits limits, Clock clock) {
        this.jdbc = jdbc; this.pages = pages; this.limits = limits; this.clock = clock;
    }

    @Transactional
    public UUID create(int first, int last) {
        limits.requireConfigured(); validateRange(first, last); lockGate();
        if (open().isPresent()) throw new OntongApiClient.Failure("SWEEP_ALREADY_OPEN");
        return insert(first, last, "MANUAL");
    }

    @Transactional
    public Optional<UUID> scheduled(int first, int last, Duration interval) {
        limits.requireConfigured(); validateRange(first, last);
        if (interval.isZero() || interval.isNegative()) throw new IllegalArgumentException("정기 수집 간격을 설정해주세요.");
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
        if (!sweep.state().equals("ACTIVE")) return new Work(sweep.state(), id, null, sweep.nextPage());
        var existing = jdbc.sql("SELECT run_id, outcome FROM ontong_collection_sweep_pages WHERE sweep_id = :id AND page_number = :page")
                .param("id", id).param("page", sweep.nextPage())
                .query((rs, row) -> new Work(rs.getString("outcome"), id, rs.getObject("run_id", UUID.class), sweep.nextPage())).optional();
        if (existing.isPresent()) {
            var work = existing.get();
            if (work.kind().equals("COMPLETED")) return new Work("SKIP", id, work.runId(), work.page());
            var page = pages.pageStatus(work.runId());
            if (page.responseStored()) return new Work("REPLAY", id, work.runId(), work.page());
            if (page.state().equals("FETCH_FAILED")) {
                pause(work, page.failureCode()); return new Work("PAUSED", id, work.runId(), work.page());
            }
            return new Work("WAITING_RESPONSE", id, work.runId(), work.page());
        }
        limits.requireConfigured();
        var run = UUID.randomUUID();
        pages.begin(run, sweep.nextPage());
        jdbc.sql("INSERT INTO ontong_collection_sweep_pages(sweep_id, page_number, run_id, outcome) VALUES (:id, :page, :run, 'REQUESTED')")
                .param("id", id).param("page", sweep.nextPage()).param("run", run).update();
        return new Work("FETCH", id, run, sweep.nextPage());
    }

    @Transactional
    public void finish(Work work, String outcome, String failureCode) {
        if (!List.of("COMPLETED", "PARTIAL").contains(outcome)) throw new IllegalArgumentException();
        lockGate();
        var sweep = find(work.sweepId());
        if (!current(sweep, work)) return;
        updatePage(work, outcome, failureCode);
        boolean last = work.page() == sweep.lastPage();
        boolean partial = jdbc.sql("SELECT EXISTS(SELECT 1 FROM ontong_collection_sweep_pages WHERE sweep_id = :id AND outcome <> 'COMPLETED')")
                .param("id", work.sweepId()).query(Boolean.class).single();
        jdbc.sql("""
                UPDATE ontong_collection_sweeps SET next_page = :next, state = :state, updated_at = :now,
                    completed_at = :completed, failure_code = :code,
                    last_successful_at = CASE WHEN :success THEN :now ELSE last_successful_at END WHERE id = :id
                """).param("id", work.sweepId()).param("next", work.page() + 1)
                .param("state", last ? partial ? "PARTIAL" : "COMPLETED" : "ACTIVE")
                .param("now", now()).param("completed", last ? now() : null)
                .param("code", last && partial ? "PAGES_REQUIRE_REVIEW" : null).param("success", outcome.equals("COMPLETED")).update();
    }

    @Transactional
    public void pause(Work work, String code) {
        lockGate();
        var sweep = find(work.sweepId());
        if (!current(sweep, work)) return;
        updatePage(work, "FAILED", code);
        jdbc.sql("UPDATE ontong_collection_sweeps SET state = 'PAUSED', failure_code = :code, updated_at = :now WHERE id = :id")
                .param("id", work.sweepId()).param("code", code).param("now", now()).update();
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
        jdbc.sql("UPDATE ontong_collection_sweeps SET state = 'ACTIVE', next_page = :page, completed_at = NULL, failure_code = NULL, updated_at = :now WHERE id = :id")
                .param("id", id).param("page", page).param("now", now()).update();
    }

    @Transactional
    public void abandon(UUID id) {
        lockGate(); find(id);
        jdbc.sql("UPDATE ontong_collection_sweeps SET state = 'ABANDONED', completed_at = :now, updated_at = :now, failure_code = 'OPERATOR_ABANDONED' WHERE id = :id AND state IN ('ACTIVE','PAUSED','PARTIAL')")
                .param("id", id).param("now", now()).update();
    }

    public Sweep find(UUID id) {
        return jdbc.sql("SELECT * FROM ontong_collection_sweeps WHERE id = :id").param("id", id)
                .query((rs, row) -> new Sweep(id, rs.getString("origin"), rs.getInt("first_page"), rs.getInt("last_page"),
                        rs.getInt("next_page"), rs.getString("state"), rs.getString("failure_code"))).optional()
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
        jdbc.sql("INSERT INTO ontong_collection_sweeps(id, origin, first_page, last_page, next_page, state, started_at, updated_at) VALUES (:id, :origin, :first, :last, :first, 'ACTIVE', :now, :now)")
                .param("id", id).param("origin", origin).param("first", first).param("last", last).param("now", now()).update();
        return id;
    }
    private void updatePage(Work work, String outcome, String code) {
        int updated = jdbc.sql("UPDATE ontong_collection_sweep_pages SET outcome = :outcome, failure_code = :code WHERE sweep_id = :id AND page_number = :page AND run_id = :run")
                .param("id", work.sweepId()).param("page", work.page()).param("run", work.runId()).param("outcome", outcome).param("code", code).update();
        if (updated != 1) throw new OntongApiClient.Failure("SWEEP_WORK_CHANGED");
    }
    private boolean current(Sweep sweep, Work work) { return sweep.state().equals("ACTIVE") && sweep.nextPage() == work.page(); }
    private void lockGate() { jdbc.sql("SELECT id FROM ontong_collection_request_gate WHERE id = 1 FOR UPDATE").query(Integer.class).single(); }
    private OffsetDateTime now() { return clock.instant().atOffset(ZoneOffset.UTC); }
    static void validateRange(int first, int last) { if (first < 1 || last < first || last > 1000) throw new IllegalArgumentException("수집 페이지 범위는 1~1000 안에서 지정해주세요."); }
    public record Sweep(UUID id, String origin, int firstPage, int lastPage, int nextPage, String state, String failureCode) {}
    public record Work(String kind, UUID sweepId, UUID runId, int page) {}
}
