package kr.youthpolicymate.member;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.UUID;

/** 서비스 중지 후 전용 명령에서만 생성한다. 웹 서버 빈으로 등록하지 않는다. */
final class EmailKeyRotation {
    private final JdbcClient jdbc;
    private final PlatformTransactionManager transactions;
    private final EmailCrypto current;

    EmailKeyRotation(JdbcClient jdbc, PlatformTransactionManager transactions, EmailCrypto current) {
        if (!current.ready()) throw new IllegalArgumentException("현재 이메일 암호화 키를 설정해주세요.");
        this.jdbc = jdbc; this.transactions = transactions; this.current = current;
    }

    Counts check() {
        var transaction = new TransactionTemplate(transactions);
        transaction.setReadOnly(true);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        return transaction.execute(status -> process(null));
    }

    Counts apply(EmailCrypto replacement) {
        if (!replacement.ready() || current.sameKey(replacement))
            throw new IllegalArgumentException("현재 키와 다른 새 이메일 암호화 키를 설정해주세요.");
        return new TransactionTemplate(transactions).execute(status -> {
            // 실행 중 쓰기 작업과 겹치면 기다리지 않고 중단한다. API 중지 확인을 대신하지는 않는다.
            jdbc.sql("LOCK TABLE members, member_email_settings, member_email_outbox IN EXCLUSIVE MODE NOWAIT").update();
            var counts = process(replacement);
            jdbc.sql("UPDATE member_email_settings SET code_hash = NULL, expires_at = NULL WHERE code_hash IS NOT NULL").update();
            jdbc.sql("""
                    UPDATE member_email_outbox SET code_cipher = NULL,
                        finished_at = CASE WHEN state = 'PENDING' THEN CURRENT_TIMESTAMP ELSE finished_at END,
                        state = CASE WHEN state = 'PENDING' THEN 'CANCELED' ELSE state END
                    WHERE kind = 'VERIFICATION' AND (code_cipher IS NOT NULL OR state = 'PENDING')
                    """).update();
            return counts;
        });
    }

    private Counts process(EmailCrypto replacement) {
        var addresses = jdbc.sql("SELECT member_id, version, address_cipher FROM member_email_settings ORDER BY member_id")
                .query((rs, row) -> new Address(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getString(3))).list();
        for (var address : addresses) {
            String context = MemberEmailStore.context(address.member(), address.version(), "address");
            String plain = current.decrypt(context, address.cipher());
            if (replacement != null) {
                jdbc.sql("UPDATE member_email_settings SET address_cipher = :cipher WHERE member_id = :member")
                        .param("cipher", replacement.encrypt(context, plain)).param("member", address.member()).update();
            }
        }
        long codes = jdbc.sql("SELECT count(*) FROM member_email_settings WHERE code_hash IS NOT NULL").query(Long.class).single();
        long pending = jdbc.sql("SELECT count(*) FROM member_email_outbox WHERE kind = 'VERIFICATION' AND state = 'PENDING'")
                .query(Long.class).single();
        return new Counts(addresses.size(), codes, pending);
    }

    record Counts(long addresses, long codes, long pending) {}
    private record Address(UUID member, UUID version, String cipher) {}
}
