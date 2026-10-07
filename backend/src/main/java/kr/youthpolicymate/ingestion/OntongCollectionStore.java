package kr.youthpolicymate.ingestion;

import kr.youthpolicymate.policy.catalog.PolicyCatalogStore;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static kr.youthpolicymate.policy.SeoulTime.SEOUL;

@Repository
@Profile("!preview")
public class OntongCollectionStore {
    /** 요청 배정·발송 시작·범위 진행을 직렬화하는 advisory 잠금 키. AI 자동 처리 키(794631028)와 겹치지 않게 둔다. */
    static final long REQUEST_LOCK = 794631029L;
    private final JdbcClient jdbc;
    private final NamedParameterJdbcTemplate batchJdbc;
    private final PolicyCatalogStore catalog;
    private final OntongPolicyCapture parser;
    private final Clock clock;
    private final OntongProperties properties;

    OntongCollectionStore(JdbcClient jdbc, NamedParameterJdbcTemplate batchJdbc, PolicyCatalogStore catalog,
                          OntongPolicyCapture parser, Clock clock, OntongProperties properties) {
        this.jdbc = jdbc;
        this.batchJdbc = batchJdbc;
        this.catalog = catalog;
        this.parser = parser;
        this.clock = clock;
        this.properties = properties;
    }

    /**
     * 호출한 트랜잭션이 끝날 때까지 유지하는 잠금을 기다려 얻는다. 외부 응답 대기 중에는 잡지 않는다.
     * 판정 문장과 합치지 않고 먼저 실행해야 이후 문장이 앞선 트랜잭션의 커밋을 본다.
     */
    static void lockRequests(JdbcClient jdbc) {
        jdbc.sql("SELECT 1 FROM pg_advisory_xact_lock(" + REQUEST_LOCK + ")").query(Integer.class).single();
    }

    @Transactional
    public void begin(UUID runId, int page) {
        lockRequests(jdbc);
        var now = clock.instant();
        if (properties.limited()) {
            var next = nextRequestAt();
            if (next.isPresent() && now.isBefore(next.get())) throw new OntongApiClient.Failure("LOCAL_REQUEST_INTERVAL");
            if (requestsOn(LocalDate.ofInstant(now, SEOUL)) >= properties.dailyLimit()) throw new OntongApiClient.Failure("LOCAL_DAILY_LIMIT");
        }
        // 이 순번을 커밋한 뒤 호출한다. 같은 실행 ID로 외부 요청을 반복하지 않는다.
        int inserted = jdbc.sql("""
                INSERT INTO ontong_collection_pages(run_id, page_number, state, started_at)
                VALUES (:id, :page, 'FETCHING', :at) ON CONFLICT (run_id) DO NOTHING
                """).param("id", runId).param("page", page).param("at", now.atOffset(ZoneOffset.UTC)).update();
        if (inserted != 1) throw new OntongApiClient.Failure("REQUEST_ALREADY_ATTEMPTED");
    }

    @Transactional
    public void startDispatch(UUID runId) {
        lockRequests(jdbc);
        // 늦게 시작한 요청이 이후 예약을 추월해 짧은 간격으로 발송되지 않게 한다.
        if (properties.limited() && jdbc.sql("""
                SELECT EXISTS(SELECT 1 FROM ontong_collection_pages WHERE request_sequence >
                    (SELECT request_sequence FROM ontong_collection_pages WHERE run_id = :id))
                """).param("id", runId).query(Boolean.class).single())
            throw new OntongApiClient.Failure("REQUEST_RESERVATION_CHANGED");
        int updated = jdbc.sql("UPDATE ontong_collection_pages SET dispatch_started_at = :now WHERE run_id = :id AND state = 'FETCHING' AND dispatch_started_at IS NULL")
                .param("id", runId).param("now", clock.instant().atOffset(ZoneOffset.UTC)).update();
        if (updated != 1) throw new OntongApiClient.Failure("REQUEST_ALREADY_ATTEMPTED");
    }

    /** 마지막 요청 예약·발송 시작 중 늦은 시각에 최소 간격을 더한다. 기록이 없으면 바로 요청할 수 있다. */
    private Optional<Instant> nextRequestAt() {
        return jdbc.sql("SELECT max(GREATEST(started_at, dispatch_started_at)) FROM ontong_collection_pages")
                .query(OffsetDateTime.class).optional()
                .map(at -> at.toInstant().plus(properties.interval()));
    }

    public String requestStatus() {
        var day = LocalDate.ofInstant(clock.instant(), SEOUL);
        long count = requestsOn(day);
        return "서울 날짜 " + day + " | 요청 예약 " + count + " | 일일 한도 " + (properties.limited() ? properties.dailyLimit() : "미설정")
                + " | 최소 간격(초) " + properties.interval().toSeconds() + " | 다음 요청 허용 시각 "
                + (properties.limited() ? nextRequestAt().map(Instant::toString).orElse("즉시") : "미설정");
    }

    private long requestsOn(LocalDate day) {
        return jdbc.sql("SELECT count(*) FROM ontong_collection_pages WHERE started_at >= :start AND started_at < :end")
                .param("start", day.atStartOfDay(SEOUL).toOffsetDateTime())
                .param("end", day.plusDays(1).atStartOfDay(SEOUL).toOffsetDateTime()).query(Long.class).single();
    }

    @Transactional
    public void received(UUID runId, OntongApiClient.Response response) {
        int updated = jdbc.sql("""
                UPDATE ontong_collection_pages SET state = 'RECEIVED', raw_body = :raw, received_at = :at
                WHERE run_id = :id AND state = 'FETCHING'
                """).param("id", runId).param("raw", response.rawBody())
                .param("at", response.receivedAt().atOffset(ZoneOffset.UTC)).update();
        if (updated != 1) throw new OntongApiClient.Failure("RESPONSE_ALREADY_RECORDED");
    }

    public void failed(UUID runId, String code, boolean hasResponse) {
        jdbc.sql("""
                UPDATE ontong_collection_pages SET state = :state, failure_code = :code
                WHERE run_id = :id AND state IN ('FETCHING', 'RECEIVED', 'INVALID_RESPONSE')
                """).param("id", runId).param("code", code)
                .param("state", hasResponse ? "INVALID_RESPONSE" : "FETCH_FAILED").update();
    }

    public Page page(UUID runId) {
        return jdbc.sql("SELECT request_sequence, page_number, received_at, raw_body FROM ontong_collection_pages WHERE run_id = :id")
                .param("id", runId)
                .query((rs, row) -> {
                    var at = rs.getObject("received_at", OffsetDateTime.class);
                    return new Page(runId, rs.getLong("request_sequence"), rs.getInt("page_number"),
                            at == null ? null : at.toInstant(), rs.getString("raw_body"));
                }).optional().orElseThrow(() -> new OntongApiClient.Failure("RUN_NOT_FOUND"));
    }

    PageStatus pageStatus(UUID runId) {
        return jdbc.sql("""
                SELECT page_number AS number, state, raw_body IS NOT NULL AS response_stored, failure_code, item_count, total_count
                FROM ontong_collection_pages WHERE run_id = :id
                """).param("id", runId).query(PageStatus.class)
                .optional().orElseThrow(() -> new OntongApiClient.Failure("RUN_NOT_FOUND"));
    }

    @Transactional
    public void prepare(UUID runId, OntongPolicyCapture.Parsed parsed) {
        var state = jdbc.sql("SELECT state FROM ontong_collection_pages WHERE run_id = :id FOR UPDATE")
                .param("id", runId).query(String.class).single();
        if (state.equals("READY")) return;
        if (!List.of("RECEIVED", "INVALID_RESPONSE").contains(state)) throw new OntongApiClient.Failure("RESPONSE_NOT_STORED");
        var batch = new MapSqlParameterSource[parsed.items().size()];
        for (int index = 0; index < batch.length; index++) {
            batch[index] = new MapSqlParameterSource("id", runId)
                    .addValue("index", index).addValue("raw", parsed.items().get(index).toString());
        }
        batchJdbc.batchUpdate("""
                INSERT INTO ontong_collection_items(run_id, item_index, raw_policy)
                VALUES (:id, :index, CAST(:raw AS jsonb))
                """, batch);
        jdbc.sql("""
                UPDATE ontong_collection_pages SET state = 'READY', failure_code = NULL,
                    item_count = :count, total_count = :total WHERE run_id = :id
                """).param("id", runId).param("count", parsed.items().size()).param("total", parsed.total()).update();
    }

    public List<Integer> pending(UUID runId) {
        return jdbc.sql("""
                SELECT item_index FROM ontong_collection_items WHERE run_id = :id
                AND outcome IN ('PENDING', 'INVALID_ITEM', 'STORE_FAILED', 'CORRECTION_CONFLICT') ORDER BY item_index
                """).param("id", runId).query(Integer.class).list();
    }

    @Transactional
    public void apply(Page page, OntongPolicyCapture.Parsed parsed, int index) {
        var outcome = lockItem(page.runId(), index);
        if (!retryable(outcome)) return;
        OntongPolicyCapture.Item item;
        try {
            item = parser.item(parsed.items().get(index));
            long copies = parsed.items().stream().filter(node -> node.path("plcyNo").isString()
                    && node.path("plcyNo").asString().strip().equals(item.number())).count();
            if (copies != 1) throw new IllegalArgumentException();
        } catch (IllegalArgumentException exception) {
            finishItem(page.runId(), index, null, "INVALID_ITEM");
            return;
        }
        var result = catalog.importPolicy(item.number(), item.content(), item.rawPolicy(),
                parsed.capturedAt(), parsed.hash(), item.contentHash(), page.sequence());
        // 정책 반영과 완료 기록을 함께 커밋한다. 중간 실패 시 둘 다 롤백한다.
        finishItem(page.runId(), index, item.number(), result.name());
    }

    @Transactional
    public void itemFailed(UUID runId, int index) {
        if (retryable(lockItem(runId, index))) finishItem(runId, index, null, "STORE_FAILED");
    }

    private String lockItem(UUID runId, int index) {
        return jdbc.sql("SELECT outcome FROM ontong_collection_items WHERE run_id = :id AND item_index = :index FOR UPDATE")
                .param("id", runId).param("index", index).query(String.class).single();
    }

    private boolean retryable(String outcome) { return List.of("PENDING", "INVALID_ITEM", "STORE_FAILED", "CORRECTION_CONFLICT").contains(outcome); }

    private void finishItem(UUID runId, int index, String number, String outcome) {
        int attempt = jdbc.sql("""
                UPDATE ontong_collection_items SET policy_number = :number, outcome = :outcome,
                    attempts = attempts + 1, updated_at = CURRENT_TIMESTAMP
                WHERE run_id = :id AND item_index = :index RETURNING attempts
                """).param("id", runId).param("index", index).param("number", number).param("outcome", outcome)
                .query(Integer.class).single();
        jdbc.sql("""
                INSERT INTO ontong_collection_item_attempts(run_id, item_index, attempt, outcome)
                VALUES (:id, :index, :attempt, :outcome)
                """).param("id", runId).param("index", index).param("attempt", attempt).param("outcome", outcome).update();
    }

    public List<String> status(UUID runId) {
        return jdbc.sql("""
                SELECT p.run_id, p.page_number, p.state, p.failure_code, p.started_at, p.received_at,
                    p.total_count, p.item_count,
                    count(i.*) FILTER (WHERE i.outcome IN ('APPLIED','UNCHANGED','REPLAYED','STALE')) AS done,
                    count(i.*) FILTER (WHERE i.outcome IN ('INVALID_ITEM','STORE_FAILED','CORRECTION_CONFLICT')) AS failed
                FROM ontong_collection_pages p LEFT JOIN ontong_collection_items i ON p.run_id = i.run_id
                WHERE (:all OR p.run_id = :id)
                GROUP BY p.request_sequence ORDER BY p.request_sequence DESC LIMIT 10
                """).param("all", runId == null).param("id", runId == null ? new UUID(0, 0) : runId)
                .query((rs, row) -> "실행 " + rs.getString("run_id") + " | 페이지 " + rs.getInt("page_number")
                        + " | " + rs.getString("state") + " | 처리 " + rs.getLong("done")
                        + "/" + rs.getObject("item_count") + " | 실패 " + rs.getLong("failed")
                        + " | 원천 전체 건수 " + rs.getObject("total_count")
                        + " | 요청 " + rs.getObject("started_at") + " | 수신 " + rs.getObject("received_at")
                        + " | 오류 " + rs.getString("failure_code")).list();
    }

    public List<String> itemStatus(UUID runId) {
        return jdbc.sql("SELECT item_index, policy_number, outcome, attempts FROM ontong_collection_items WHERE run_id = :id ORDER BY item_index")
                .param("id", runId).query((rs, row) -> "항목 " + (rs.getInt(1) + 1) + " | 정책 " + rs.getString(2)
                        + " | " + rs.getString(3) + " | 처리 시도 " + rs.getInt(4)).list();
    }

    public record Page(UUID runId, long sequence, int number, Instant receivedAt, String rawBody) {}
    record PageStatus(int number, String state, boolean responseStored, String failureCode, Integer itemCount, Long totalCount) {}
}
