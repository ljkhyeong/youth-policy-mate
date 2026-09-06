package db.migration;

import kr.youthpolicymate.policy.catalog.PolicyRecruitmentWindow;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import tools.jackson.databind.ObjectMapper;

public class V18__index_policy_recruitment extends BaseJavaMigration {
    @Override
    public void migrate(Context context) throws Exception {
        var connection = context.getConnection();
        try (var ddl = connection.createStatement()) {
            ddl.execute("""
                    ALTER TABLE policies
                        ADD COLUMN recruitment_kind text NOT NULL DEFAULT 'UNKNOWN',
                        ADD COLUMN recruitment_opens_at timestamptz,
                        ADD COLUMN recruitment_closes_at timestamptz,
                        ADD CONSTRAINT policies_recruitment_window CHECK (
                            (recruitment_kind = 'PERIOD' AND recruitment_opens_at IS NOT NULL
                                AND recruitment_closes_at IS NOT NULL AND recruitment_opens_at < recruitment_closes_at)
                            OR (recruitment_kind IN ('ROLLING', 'UNTIL_EXHAUSTED', 'CLOSED', 'UNKNOWN')
                                AND recruitment_opens_at IS NULL AND recruitment_closes_at IS NULL))
                    """);
            ddl.execute("CREATE INDEX policies_recruitment_kind ON policies(recruitment_kind)");
        }
        var mapper = new ObjectMapper();
        try (var query = connection.createStatement(); var update = connection.prepareStatement("""
                UPDATE policies SET recruitment_kind = ?, recruitment_opens_at = ?, recruitment_closes_at = ?
                WHERE policy_number = ?
                """)) {
            query.setFetchSize(256);
            try (var rows = query.executeQuery("""
                    SELECT p.policy_number, p.content_hash, s.raw_policy
                    FROM policies p
                    JOIN policy_revisions r ON r.policy_number = p.policy_number AND r.revision = p.current_revision
                    JOIN policy_source_snapshots s ON s.id = r.source_snapshot_id
                    WHERE p.current_revision > 0
                    """)) {
                while (rows.next()) {
                    var number = rows.getString("policy_number");
                    var window = PolicyRecruitmentWindow.from(number, rows.getString("content_hash"),
                            mapper.readTree(rows.getString("raw_policy")));
                    update.setString(1, window.kind());
                    update.setObject(2, window.opensAt());
                    update.setObject(3, window.closesAt());
                    update.setString(4, number);
                    update.executeUpdate();
                }
            }
        }
    }
}
