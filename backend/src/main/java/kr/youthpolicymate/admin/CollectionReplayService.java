package kr.youthpolicymate.admin;

import kr.youthpolicymate.ingestion.OntongCollectionService;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
@Profile("!preview")
class CollectionReplayService {
    private final JdbcClient jdbc;
    private final OntongCollectionService collection;

    CollectionReplayService(JdbcClient jdbc, OntongCollectionService collection) {
        this.jdbc = jdbc;
        this.collection = collection;
    }

    @Transactional
    public CollectionReplays.Result replay(UUID runId, int index, UUID actorId, CollectionReplays.Request request) {
        var item = jdbc.sql("SELECT outcome, attempts FROM ontong_collection_items WHERE run_id = :run AND item_index = :index FOR UPDATE")
                .param("run", runId).param("index", index)
                .query((rs, row) -> new ItemState(rs.getString("outcome"), rs.getInt("attempts")))
                .optional().orElseThrow(CollectionReplays.Changed::new);
        var existing = jdbc.sql("SELECT * FROM admin_collection_replays WHERE request_id = :id")
                .param("id", request.requestId()).query((rs, row) -> result(rs)).optional();
        if (existing.isPresent()) {
            var replay = existing.orElseThrow();
            if (!replay.runId().equals(runId) || replay.itemIndex() != index || !replay.actorId().equals(actorId)
                    || replay.expectedAttempts() != request.expectedAttempts() || !replay.reason().equals(request.reason().strip()))
                throw new CollectionReplays.Changed();
            return replay;
        }
        if (!List.of("INVALID_ITEM", "STORE_FAILED", "CORRECTION_CONFLICT").contains(item.outcome()) || item.attempts() != request.expectedAttempts())
            throw new CollectionReplays.Changed();

        collection.applyStoredItem(runId, index);
        // 항목 반영·시도 이력·관리자 사유를 함께 커밋한다. 저장 실패는 전부 롤백한다.
        return jdbc.sql("""
                INSERT INTO admin_collection_replays(request_id, run_id, item_index, actor_id, reason,
                    expected_attempts, attempt, outcome, policy_number, policy_revision)
                SELECT :request, i.run_id, i.item_index, :actor, :reason, :expected, i.attempts, i.outcome, i.policy_number,
                    CASE WHEN i.outcome IN ('APPLIED', 'UNCHANGED', 'REPLAYED') THEN p.current_revision END
                FROM ontong_collection_items i LEFT JOIN policies p ON p.policy_number = i.policy_number
                WHERE i.run_id = :run AND i.item_index = :index
                RETURNING *
                """).param("request", request.requestId()).param("actor", actorId).param("reason", request.reason().strip())
                .param("expected", request.expectedAttempts()).param("run", runId).param("index", index)
                .query((rs, row) -> result(rs)).single();
    }

    @Transactional(readOnly = true)
    public CollectionReplays.Page list(int page, int pageSize) {
        var items = jdbc.sql("SELECT * FROM admin_collection_replays ORDER BY processed_at DESC, request_id LIMIT :limit OFFSET :offset")
                .param("limit", pageSize + 1).param("offset", (page - 1) * pageSize).query((rs, row) -> result(rs)).list();
        var hasNext = items.size() > pageSize;
        return new CollectionReplays.Page(hasNext ? items.subList(0, pageSize) : items, page, pageSize, hasNext);
    }

    private static CollectionReplays.Result result(ResultSet rs) throws SQLException {
        return new CollectionReplays.Result(rs.getObject("request_id", UUID.class), rs.getObject("run_id", UUID.class),
                rs.getInt("item_index"), rs.getObject("actor_id", UUID.class), rs.getString("reason"), rs.getInt("expected_attempts"),
                rs.getInt("attempt"), CollectionReplays.Outcome.valueOf(rs.getString("outcome")), rs.getString("policy_number"),
                rs.getObject("policy_revision", Long.class), rs.getObject("processed_at", OffsetDateTime.class).toInstant());
    }

    private record ItemState(String outcome, int attempts) {}
}
