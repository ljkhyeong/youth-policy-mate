package kr.youthpolicymate.member;

import org.springframework.core.env.Environment;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

@Component
@Profile("!preview")
public class EmailCrypto {
    private final byte[] key;
    private final SecureRandom random = new SecureRandom();
    public EmailCrypto(Environment environment) {
        String configured = environment.getProperty("app.email.encryption-key", "");
        try { key = configured.isBlank() ? null : Base64.getDecoder().decode(configured); }
        catch (IllegalArgumentException invalid) { throw new IllegalStateException("이메일 암호화 키 형식을 확인해주세요."); }
        if (key != null && key.length != 32) throw new IllegalStateException("이메일 암호화 키는 32바이트여야 합니다.");
    }
    boolean ready() { return key != null; }
    String code() { return "%08d".formatted(random.nextInt(100_000_000)); }
    String hash(String context, String value) {
        try {
            var mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return Base64.getEncoder().encodeToString(mac.doFinal((context + ":" + value).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception failure) { throw new IllegalStateException("확인 코드를 처리할 수 없습니다."); }
    }
    String encrypt(String context, String value) {
        try {
            byte[] iv = new byte[12]; random.nextBytes(iv);
            var cipher = cipher(Cipher.ENCRYPT_MODE, context, iv);
            byte[] body = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(ByteBuffer.allocate(iv.length + body.length).put(iv).put(body).array());
        } catch (Exception failure) { throw new IllegalStateException("이메일 정보를 암호화할 수 없습니다."); }
    }
    String decrypt(String context, String value) {
        try {
            var bytes = ByteBuffer.wrap(Base64.getDecoder().decode(value));
            byte[] iv = new byte[12]; bytes.get(iv);
            byte[] body = new byte[bytes.remaining()]; bytes.get(body);
            return new String(cipher(Cipher.DECRYPT_MODE, context, iv).doFinal(body), StandardCharsets.UTF_8);
        } catch (Exception failure) { throw new IllegalStateException("이메일 정보를 복호화할 수 없습니다."); }
    }
    private Cipher cipher(int mode, String context, byte[] iv) throws Exception {
        var cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(mode, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
        cipher.updateAAD(context.getBytes(StandardCharsets.UTF_8));
        return cipher;
    }
}
