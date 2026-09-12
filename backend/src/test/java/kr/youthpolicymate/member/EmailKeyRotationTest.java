package kr.youthpolicymate.member;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static kr.youthpolicymate.member.MemberEmailStore.context;

@Testcontainers
class EmailKeyRotationTest {
    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18.6-alpine");
    private static final String OLD = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=";
    private static final String NEXT = "AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE=";
    private static final UUID FIRST = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID SECOND = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static JdbcClient jdbc;
    private static JdbcTransactionManager transactions;
    private final EmailCrypto old = new EmailCrypto(OLD);
    private final EmailCrypto next = new EmailCrypto(NEXT);
    private EmailKeyRotation rotation;

    @BeforeAll
    static void database() {
        var source = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        Flyway.configure().dataSource(source).load().migrate();
        jdbc = JdbcClient.create(source);
        transactions = new JdbcTransactionManager(source);
    }

    @BeforeEach
    void prepare() {
        jdbc.sql("TRUNCATE members, policies CASCADE").update();
        rotation = new EmailKeyRotation(jdbc, transactions, old);
        member(FIRST, true);
        member(SECOND, false);
        for (String state : List.of("PENDING", "SENT", "UNKNOWN", "SENDING", "FAILED")) {
            jdbc.sql("""
                    INSERT INTO member_email_outbox(id, member_id, settings_version, kind, code_cipher, state, created_at, expires_at)
                    VALUES (:id, :member, :member, 'VERIFICATION', :code, :state, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP + interval '10 minutes')
                    """).param("id", UUID.randomUUID()).param("member", SECOND).param("state", state)
                    .param("code", state.equals("PENDING") ? old.encrypt(context(SECOND, SECOND, "code"), "12345678") : null).update();
        }
        jdbc.sql("INSERT INTO policies(policy_number) VALUES ('test-policy')").update();
        jdbc.sql("""
                INSERT INTO member_notifications(id, member_id, policy_number, generation, policy_revision, kind, title, message)
                VALUES (:member, :member, 'test-policy', :member, 1, 'CHANGED', '변경 알림', '내용 확인')
                """).param("member", FIRST).update();
        jdbc.sql("""
                INSERT INTO member_email_outbox(id, member_id, settings_version, kind, notification_id, state, created_at, unsubscribe_token_hash)
                VALUES (:member, :member, :member, 'POLICY', :member, 'PENDING', CURRENT_TIMESTAMP, 'token-hash')
                """).param("member", FIRST).update();
    }

    private void member(UUID id, boolean verified) {
        jdbc.sql("INSERT INTO members(id, provider, provider_subject, display_name) VALUES (:id, 'kakao', :subject, '검증 회원')")
                .param("id", id).param("subject", id.toString()).update();
        jdbc.sql("""
                INSERT INTO member_email_settings(member_id, version, address_cipher, verified_at, enabled, consented_at, code_hash, expires_at)
                VALUES (:id, :id, :cipher, CASE WHEN :verified THEN CURRENT_TIMESTAMP END, :verified,
                    CASE WHEN :verified THEN CURRENT_TIMESTAMP END, :hash,
                    CASE WHEN NOT :verified THEN CURRENT_TIMESTAMP + interval '10 minutes' END)
                """).param("id", id).param("cipher", old.encrypt(context(id, id, "address"), address(id)))
                .param("verified", verified).param("hash", verified ? null : old.hash(context(id, id, "code"), "12345678")).update();
    }

    @Test @DisplayName("점검은 주소를 복호화하되 암호문·인증 코드·발송 기록을 변경하지 않는다")
    void checksWithoutWrites() {
        var before = snapshot();
        assertThat(rotation.check()).isEqualTo(new EmailKeyRotation.Counts(2, 1, 1));
        assertThat(snapshot()).isEqualTo(before);
    }

    @Test @DisplayName("키 교체는 주소·동의·정책 발송을 유지하고 미완료 인증만 만료한다")
    void rotatesAndPreservesConsentAndDelivery() {
        var verified = jdbc.sql("SELECT version, verified_at, enabled, consented_at, delivery_issue FROM member_email_settings WHERE member_id = :id")
                .param("id", FIRST).query().singleRow();
        var policy = jdbc.sql("SELECT * FROM member_email_outbox WHERE kind = 'POLICY'").query().singleRow();
        var sent = jdbc.sql("SELECT * FROM member_email_outbox WHERE kind = 'VERIFICATION' AND state <> 'PENDING' ORDER BY id").query().listOfRows();
        assertThat(rotation.apply(next)).isEqualTo(new EmailKeyRotation.Counts(2, 1, 1));
        for (UUID id : List.of(FIRST, SECOND)) {
            assertThat(next.decrypt(context(id, id, "address"), cipher(id))).isEqualTo(address(id));
            assertThatThrownBy(() -> old.decrypt(context(id, id, "address"), cipher(id))).isInstanceOf(IllegalStateException.class);
        }
        assertThat(jdbc.sql("SELECT version, verified_at, enabled, consented_at, delivery_issue FROM member_email_settings WHERE member_id = :id")
                .param("id", FIRST).query().singleRow()).isEqualTo(verified);
        assertThat(jdbc.sql("SELECT * FROM member_email_outbox WHERE kind = 'POLICY'").query().singleRow()).isEqualTo(policy);
        assertThat(jdbc.sql("SELECT * FROM member_email_outbox WHERE kind = 'VERIFICATION' AND state NOT IN ('PENDING','CANCELED') ORDER BY id")
                .query().listOfRows()).isEqualTo(sent);
        assertThat(jdbc.sql("SELECT count(*) FROM member_email_settings WHERE code_hash IS NOT NULL OR expires_at IS NOT NULL").query(Long.class).single()).isZero();
        assertThat(jdbc.sql("SELECT count(*) FROM member_email_outbox WHERE code_cipher IS NOT NULL OR (kind = 'VERIFICATION' AND state = 'PENDING')").query(Long.class).single()).isZero();
        assertThat(jdbc.sql("SELECT count(*) FROM member_email_outbox WHERE state = 'CANCELED' AND finished_at IS NOT NULL").query(Long.class).single()).isEqualTo(1);
        assertThat(new EmailKeyRotation(jdbc, transactions, next).check()).isEqualTo(new EmailKeyRotation.Counts(2, 0, 0));
        var after = snapshot();
        assertThatThrownBy(() -> rotation.apply(next)).isInstanceOf(IllegalStateException.class);
        assertThat(snapshot()).isEqualTo(after);
    }

    @Test @DisplayName("뒤쪽 주소가 손상되면 앞서 재암호화한 주소까지 롤백한다")
    void rollsBackOnCorruptAddress() {
        jdbc.sql("UPDATE member_email_settings SET address_cipher = :cipher WHERE member_id = :id")
                .param("id", SECOND).param("cipher", old.encrypt("wrong-context", "private@example.test")).update();
        var before = snapshot();
        assertThatThrownBy(() -> rotation.apply(next)).isInstanceOf(IllegalStateException.class).hasMessage("이메일 정보를 복호화할 수 없습니다.");
        assertThat(snapshot()).isEqualTo(before);
        assertThat(old.decrypt(context(FIRST, FIRST, "address"), cipher(FIRST))).isEqualTo(address(FIRST));
    }

    @Test @DisplayName("현재 키·새 키가 없거나 같으면 교체하지 않는다")
    void rejectsInvalidKeys() {
        var before = snapshot();
        assertThatThrownBy(() -> new EmailKeyRotation(jdbc, transactions, new EmailCrypto(""))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> rotation.apply(new EmailCrypto(""))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> rotation.apply(new EmailCrypto(OLD))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EmailCrypto("invalid-secret")).isInstanceOf(IllegalStateException.class).hasMessageNotContaining("invalid-secret");
        assertThatThrownBy(() -> new EmailCrypto("AA==")).isInstanceOf(IllegalStateException.class);
        assertThat(snapshot()).isEqualTo(before);
    }

    @Test @DisplayName("회원 쓰기 작업의 잠금이 있으면 대기하거나 일부 교체하지 않고 중단한다")
    void refusesConcurrentWriter() {
        var before = snapshot();
        try (var executor = Executors.newSingleThreadExecutor()) {
            new TransactionTemplate(transactions).executeWithoutResult(status -> {
                jdbc.sql("SELECT id FROM members WHERE id = :id FOR UPDATE").param("id", FIRST).query(UUID.class).single();
                var attempt = executor.submit(() -> catchThrowable(() -> rotation.apply(next)));
                try {
                    assertThat(attempt.get(5, TimeUnit.SECONDS)).isInstanceOf(org.springframework.dao.DataAccessException.class)
                            .rootCause().isInstanceOfSatisfying(java.sql.SQLException.class,
                                    failure -> assertThat(failure.getSQLState()).isEqualTo("55P03"));
                }
                catch (Exception failure) { throw new AssertionError(failure); }
            });
        }
        assertThat(snapshot()).isEqualTo(before);
        assertThat(rotation.apply(next).addresses()).isEqualTo(2);
    }

    @Test @DisplayName("독립 명령은 정기 작업 설정이 켜져 있어도 점검·교체만 실행하고 비밀값을 출력하지 않는다")
    void runsIsolatedCommand() throws Exception {
        var before = snapshot();
        var check = command(OLD, NEXT, "check");
        assertThat(check.exit()).isZero();
        assertThat(check.output()).contains("DB 변경 없음", "주소 2건", "만료 대상 확인 코드 1건");
        assertThat(snapshot()).isEqualTo(before);
        var apply = command(OLD, NEXT, "apply");
        assertThat(apply.exit()).isZero();
        assertThat(apply.output()).contains("키 교체 완료", "취소한 확인 메일 1건");
        assertThat(command(NEXT, "", "check").exit()).isZero();
        assertThat(command(OLD, NEXT, "check").exit()).isEqualTo(1);
        var invalid = command("invalid-secret", NEXT, "apply");
        assertThat(invalid.exit()).isEqualTo(1);
        assertThat(invalid.output()).contains("완료하지 못했습니다").doesNotContain("invalid-secret", "Exception");
        for (var result : List.of(check, apply, invalid))
            assertThat(result.output()).doesNotContain(OLD, NEXT, address(FIRST), address(SECOND), "12345678", "Hikari", "Tomcat");
        assertThat(command(OLD, NEXT, "apply", "unapproved-argument").exit()).isEqualTo(1);
    }

    private CommandResult command(String current, String replacement, String... args) throws Exception {
        var command = new java.util.ArrayList<>(List.of(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-cp", System.getProperty("email.rotation.classpath"), EmailKeyRotationCommand.class.getName()));
        command.addAll(List.of(args));
        var process = new ProcessBuilder(command).redirectErrorStream(true);
        process.environment().keySet().removeIf(key -> key.startsWith("SPRING_") || key.startsWith("EMAIL_")
                || key.startsWith("DB_") || key.equals("JAVA_TOOL_OPTIONS") || key.equals("JDK_JAVA_OPTIONS") || key.equals("_JAVA_OPTIONS"));
        process.environment().putAll(Map.of("DB_HOST", postgres.getHost(), "DB_PORT", postgres.getMappedPort(5432).toString(),
                "DB_NAME", postgres.getDatabaseName(), "DB_USER", postgres.getUsername(), "DB_PASSWORD", postgres.getPassword(),
                "EMAIL_ENCRYPTION_KEY", current, "EMAIL_ENCRYPTION_NEXT_KEY", replacement,
                "EMAIL_ENABLED", "true", "AI_AUTO_ENABLED", "true", "ONTONG_COLLECTION_SCHEDULE_ENABLED", "true"));
        process.environment().put("REMINDERS_ENABLED", "true");
        Path directory = Files.createTempDirectory("email-key-command-");
        Path output = directory.resolve("output.log");
        process.directory(directory.toFile()).redirectOutput(output.toFile());
        var child = process.start();
        try {
            assertThat(child.waitFor(30, TimeUnit.SECONDS)).isTrue();
            return new CommandResult(child.exitValue(), Files.readString(output));
        } finally {
            if (child.isAlive()) child.destroyForcibly().waitFor(5, TimeUnit.SECONDS);
            Files.deleteIfExists(output); Files.deleteIfExists(directory);
        }
    }

    private String cipher(UUID id) {
        return jdbc.sql("SELECT address_cipher FROM member_email_settings WHERE member_id = :id").param("id", id).query(String.class).single();
    }
    private List<String> snapshot() {
        return List.of(jdbc.sql("SELECT jsonb_agg(to_jsonb(s) ORDER BY member_id)::text FROM member_email_settings s").query(String.class).single(),
                jdbc.sql("SELECT jsonb_agg(to_jsonb(o) ORDER BY id)::text FROM member_email_outbox o").query(String.class).single());
    }
    private static String address(UUID id) { return id.equals(FIRST) ? "first@example.test" : "second@example.test"; }
    private record CommandResult(int exit, String output) {}
}
