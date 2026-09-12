package kr.youthpolicymate.ingestion;

import static kr.youthpolicymate.ingestion.AiDatabaseTime.dbTime;
import static kr.youthpolicymate.ingestion.AiDatabaseTime.instant;
import static kr.youthpolicymate.ingestion.AiDatabaseTime.sameDatabaseInstant;

import kr.youthpolicymate.ingestion.AiRequestBudget.Balance;
import kr.youthpolicymate.ingestion.AiRequestBudget.CostCeiling;
import kr.youthpolicymate.ingestion.PolicyAiRequestAdmission.ReservationRequired;
import kr.youthpolicymate.ingestion.PolicyAiResult.Request;
import kr.youthpolicymate.policy.PolicyObservation.Readable;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

@Repository
@Profile("!preview")
public class AiBudgetReservationStore {
    private final JdbcClient jdbcClient;

    public AiBudgetReservationStore(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    // 예산 행 잠금과 예약 INSERT를 한 트랜잭션에서 처리한다. 외부 AI 호출은 이 메서드 밖에서 수행한다.
    @Transactional
    public Attempt reserve(String reservationId, ReservationRequired required, Instant at) {
        Assert.hasText(reservationId, "AI 요청 예약 식별자가 필요합니다.");
        Objects.requireNonNull(required, "사전 판단의 예약 필요 결과가 필요합니다.");
        Objects.requireNonNull(at, "예약 시각이 필요합니다.");
        var requiredBalance = Objects.requireNonNull(required.balance(), "예약 판단에 사용한 AI 예산 잔액이 필요합니다.");
        var cost = Objects.requireNonNull(required.cost(), "요청 최대 비용이 필요합니다.");
        if (at.isBefore(cost.request().preparedAt())) throw new IllegalArgumentException("요청 준비 전에 예약할 수 없습니다.");

        var storedBudget = lockBudget(requiredBalance.budgetId());
        if (storedBudget.isEmpty()) return new Attempt(Decision.BUDGET_NOT_FOUND, Optional.empty());
        var balance = storedBudget.orElseThrow().balance();

        var sameId = findByReservationId(reservationId);
        if (sameId.isPresent()) {
            return attempt(sameId.orElseThrow().matches(requiredBalance.budgetId(), cost)
                    ? Decision.REPLAYED : Decision.RESERVATION_ID_CONFLICT, balance);
        }

        var identity = RequestIdentity.from(cost.request());
        if (findByRequest(identity).isPresent()) return attempt(Decision.REQUEST_ALREADY_RESERVED, balance);
        if (!sameBalance(balance, requiredBalance)) return attempt(Decision.STALE_BALANCE, balance);
        if (!balance.contains(at)) return attempt(Decision.BUDGET_PERIOD_INACTIVE, balance);
        if (!at.isBefore(cost.validUntil())) return attempt(Decision.COST_EXPIRED, balance);
        if (balance.remainingWon().signum() <= 0 || cost.maximumWon().compareTo(balance.remainingWon()) > 0) {
            return attempt(Decision.BUDGET_LIMIT, balance);
        }

        int inserted = insertReservation(reservationId, requiredBalance.budgetId(), identity, cost, at);
        if (inserted == 0) return classifyInsertConflict(reservationId, requiredBalance.budgetId(), cost, identity, balance);

        int updated = jdbcClient.sql("""
                update ai_budgets
                set reserved_won = reserved_won + :maximumWon,
                    updated_at = :updatedAt
                where budget_id = :budgetId
                """)
                .param("maximumWon", cost.maximumWon())
                .param("updatedAt", dbTime(at))
                .param("budgetId", balance.budgetId())
                .update();
        if (updated != 1) throw new IllegalStateException("잠근 AI 예산 잔액을 갱신하지 못했습니다.");

        var next = new Balance(balance.budgetId(), balance.startsAt(), balance.endsAt(), balance.limitWon(),
                balance.confirmedWon(), balance.reservedWon().add(cost.maximumWon()));
        return attempt(Decision.RESERVED, next);
    }

    private Optional<BudgetRow> lockBudget(String budgetId) {
        return jdbcClient.sql("""
                select budget_id, starts_at, ends_at, limit_won, confirmed_won, reserved_won
                from ai_budgets
                where budget_id = :budgetId
                for update
                """)
                .param("budgetId", budgetId)
                .query(AiBudgetReservationStore::budgetRow)
                .optional();
    }

    private Optional<StoredReservation> findByReservationId(String reservationId) {
        return jdbcClient.sql(reservationSelect() + " where reservation_id = :reservationId")
                .param("reservationId", reservationId)
                .query(AiBudgetReservationStore::storedReservation)
                .optional();
    }

    private Optional<String> findByRequest(RequestIdentity identity) {
        return jdbcClient.sql("""
                select reservation_id
                from ai_request_reservations
                where policy_id = :policyId
                  and ai_kind = :kind
                  and request_sequence = :requestSequence
                """)
                .param("policyId", identity.policyId())
                .param("kind", identity.kind())
                .param("requestSequence", identity.requestSequence())
                .query(String.class)
                .optional();
    }

    private int insertReservation(String reservationId, String budgetId, RequestIdentity identity,
                                  CostCeiling cost, Instant at) {
        return jdbcClient.sql("""
                insert into ai_request_reservations (
                    reservation_id, budget_id, policy_id, source_revision_number,
                    source_collection_sequence, source_observed_at, source_name, source_snapshot_id,
                    source_body_sha256, comparison_version, source_content_sha256, ai_kind,
                    generation_version, request_sequence, request_prepared_at, pricing_version,
                    cost_valid_until, maximum_won, phase, reserved_at, created_at, updated_at
                ) values (
                    :reservationId, :budgetId, :policyId, :sourceRevisionNumber,
                    :sourceCollectionSequence, :sourceObservedAt, :sourceName, :sourceSnapshotId,
                    :sourceBodySha256, :comparisonVersion, :sourceContentSha256, :kind,
                    :generationVersion, :requestSequence, :requestPreparedAt, :pricingVersion,
                    :costValidUntil, :maximumWon, 'HELD', :reservedAt, :reservedAt, :reservedAt
                )
                on conflict do nothing
                """)
                .param("reservationId", reservationId)
                .param("budgetId", budgetId)
                .param("policyId", identity.policyId())
                .param("sourceRevisionNumber", identity.sourceRevisionNumber())
                .param("sourceCollectionSequence", identity.sourceCollectionSequence())
                .param("sourceObservedAt", dbTime(identity.sourceObservedAt()))
                .param("sourceName", identity.sourceName())
                .param("sourceSnapshotId", identity.sourceSnapshotId())
                .param("sourceBodySha256", identity.sourceBodySha256())
                .param("comparisonVersion", identity.comparisonVersion())
                .param("sourceContentSha256", identity.sourceContentSha256())
                .param("kind", identity.kind())
                .param("generationVersion", identity.generationVersion())
                .param("requestSequence", identity.requestSequence())
                .param("requestPreparedAt", dbTime(identity.requestPreparedAt()))
                .param("pricingVersion", cost.pricingVersion())
                .param("costValidUntil", dbTime(cost.validUntil()))
                .param("maximumWon", cost.maximumWon())
                .param("reservedAt", dbTime(at))
                .update();
    }

    private Attempt classifyInsertConflict(String reservationId, String budgetId, CostCeiling cost,
                                           RequestIdentity identity, Balance balance) {
        var sameId = findByReservationId(reservationId);
        if (sameId.isPresent()) {
            return attempt(sameId.orElseThrow().matches(budgetId, cost)
                    ? Decision.REPLAYED : Decision.RESERVATION_ID_CONFLICT, balance);
        }
        if (findByRequest(identity).isPresent()) return attempt(Decision.REQUEST_ALREADY_RESERVED, balance);
        throw new IllegalStateException("AI 요청 예약 충돌의 저장 결과를 확인하지 못했습니다.");
    }

    private static String reservationSelect() {
        return """
                select reservation_id, budget_id, policy_id, source_revision_number,
                       source_collection_sequence, source_observed_at, source_name, source_snapshot_id,
                       source_body_sha256, comparison_version, source_content_sha256, ai_kind,
                       generation_version, request_sequence, request_prepared_at, pricing_version,
                       maximum_won
                from ai_request_reservations
                """;
    }

    private static BudgetRow budgetRow(ResultSet resultSet, int rowNumber) throws SQLException {
        return new BudgetRow(new Balance(
                resultSet.getString("budget_id"), instant(resultSet, "starts_at"), instant(resultSet, "ends_at"),
                resultSet.getBigDecimal("limit_won"), resultSet.getBigDecimal("confirmed_won"),
                resultSet.getBigDecimal("reserved_won")));
    }

    private static StoredReservation storedReservation(ResultSet resultSet, int rowNumber) throws SQLException {
        return new StoredReservation(
                resultSet.getString("reservation_id"), resultSet.getString("budget_id"),
                resultSet.getString("policy_id"), resultSet.getLong("source_revision_number"),
                resultSet.getLong("source_collection_sequence"), instant(resultSet, "source_observed_at"),
                resultSet.getString("source_name"), resultSet.getString("source_snapshot_id"),
                resultSet.getString("source_body_sha256"), resultSet.getString("comparison_version"),
                resultSet.getString("source_content_sha256"), resultSet.getString("ai_kind"),
                resultSet.getString("generation_version"), resultSet.getLong("request_sequence"),
                instant(resultSet, "request_prepared_at"), resultSet.getString("pricing_version"),
                resultSet.getBigDecimal("maximum_won"));
    }

    private static boolean sameBalance(Balance actual, Balance expected) {
        return actual.budgetId().equals(expected.budgetId())
                && sameDatabaseInstant(actual.startsAt(), expected.startsAt())
                && sameDatabaseInstant(actual.endsAt(), expected.endsAt())
                && sameMoney(actual.limitWon(), expected.limitWon())
                && sameMoney(actual.confirmedWon(), expected.confirmedWon())
                && sameMoney(actual.reservedWon(), expected.reservedWon());
    }

    private static boolean sameMoney(BigDecimal left, BigDecimal right) { return left.compareTo(right) == 0; }
    private static Attempt attempt(Decision decision, Balance balance) {
        return new Attempt(decision, Optional.of(balance));
    }

    public enum Decision {
        RESERVED, REPLAYED, BUDGET_NOT_FOUND, RESERVATION_ID_CONFLICT, REQUEST_ALREADY_RESERVED,
        STALE_BALANCE, BUDGET_PERIOD_INACTIVE, COST_EXPIRED, BUDGET_LIMIT
    }

    public record Attempt(Decision decision, Optional<Balance> balance) {
        public Attempt {
            Objects.requireNonNull(decision, "DB 예약 결과가 필요합니다.");
            Objects.requireNonNull(balance, "DB 예산 잔액의 존재 여부가 필요합니다.");
        }
    }

    private record BudgetRow(Balance balance) {}

    private record RequestIdentity(
            String policyId, long sourceRevisionNumber, long sourceCollectionSequence, Instant sourceObservedAt,
            String sourceName, String sourceSnapshotId, String sourceBodySha256, String comparisonVersion,
            String sourceContentSha256, String kind, String generationVersion, long requestSequence,
            Instant requestPreparedAt
    ) {
        private static RequestIdentity from(Request request) {
            var revision = request.sourceRevision();
            var observation = revision.observation();
            var readable = (Readable) observation.outcome();
            return new RequestIdentity(request.policyId(), revision.number(), observation.collectionSequence(),
                    observation.observedAt(), readable.snapshot().sourceName(), readable.snapshot().snapshotId(),
                    readable.snapshot().bodySha256(), readable.content().comparisonVersion(),
                    readable.content().sha256(), request.kind().name(), request.generationVersion(),
                    request.sequence(), request.preparedAt());
        }
    }

    private record StoredReservation(
            String reservationId, String budgetId, String policyId, long sourceRevisionNumber,
            long sourceCollectionSequence, Instant sourceObservedAt, String sourceName, String sourceSnapshotId,
            String sourceBodySha256, String comparisonVersion, String sourceContentSha256, String kind,
            String generationVersion, long requestSequence, Instant requestPreparedAt, String pricingVersion,
            BigDecimal maximumWon
    ) {
        private boolean matches(String expectedBudgetId, CostCeiling cost) {
            var identity = RequestIdentity.from(cost.request());
            return budgetId.equals(expectedBudgetId)
                    && policyId.equals(identity.policyId())
                    && sourceRevisionNumber == identity.sourceRevisionNumber()
                    && sourceCollectionSequence == identity.sourceCollectionSequence()
                    && sameDatabaseInstant(sourceObservedAt, identity.sourceObservedAt())
                    && sourceName.equals(identity.sourceName())
                    && sourceSnapshotId.equals(identity.sourceSnapshotId())
                    && sourceBodySha256.equals(identity.sourceBodySha256())
                    && comparisonVersion.equals(identity.comparisonVersion())
                    && sourceContentSha256.equals(identity.sourceContentSha256())
                    && kind.equals(identity.kind())
                    && generationVersion.equals(identity.generationVersion())
                    && requestSequence == identity.requestSequence()
                    && sameDatabaseInstant(requestPreparedAt, identity.requestPreparedAt())
                    && pricingVersion.equals(cost.pricingVersion())
                    && sameMoney(maximumWon, cost.maximumWon());
        }
    }
}
