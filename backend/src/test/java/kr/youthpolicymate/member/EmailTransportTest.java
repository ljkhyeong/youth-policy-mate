package kr.youthpolicymate.member;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.*;

class EmailTransportTest {
    private MockEnvironment environment() {
        return new MockEnvironment().withProperty("app.email.encryption-key", "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=");
    }
    @Test
    @DisplayName("이메일 암호문은 매번 다르고 다른 회원·개정의 문맥으로 복호화할 수 없다")
    void bindsEncryptedValues() {
        var crypto = new EmailCrypto(environment());
        var encrypted = crypto.encrypt("member:version:address", "first@example.test");
        assertThat(encrypted).isNotEqualTo(crypto.encrypt("member:version:address", "first@example.test"));
        assertThat(crypto.decrypt("member:version:address", encrypted)).isEqualTo("first@example.test");
        assertThatThrownBy(() -> crypto.decrypt("other:version:address", encrypted)).isInstanceOf(IllegalStateException.class);
        assertThat(new SmtpMemberEmailSender(new MockEnvironment(), crypto).available()).isFalse();
        assertThatThrownBy(() -> new SmtpMemberEmailSender(environment().withProperty("app.email.enabled", "true"), crypto))
                .isInstanceOf(IllegalStateException.class);
    }
    @Test
    @DisplayName("로컬 SMTP가 TLS를 지원하지 않으면 수신 주소와 본문을 전송하기 전에 중단한다")
    void refusesPlaintextSmtp() throws Exception {
        try (var server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress()); var pool = Executors.newSingleThreadExecutor()) {
            server.setSoTimeout(5000);
            var conversation = pool.submit(() -> {
                var commands = new ArrayList<String>();
                try (var socket = server.accept()) {
                    socket.setSoTimeout(5000);
                    var input = new BufferedReader(new InputStreamReader(socket.getInputStream(), java.nio.charset.StandardCharsets.US_ASCII));
                    var output = new PrintWriter(socket.getOutputStream(), true, java.nio.charset.StandardCharsets.US_ASCII);
                    output.print("220 localhost test SMTP\r\n"); output.flush();
                    String line;
                    while ((line = input.readLine()) != null) {
                        commands.add(line);
                        if (line.startsWith("EHLO")) output.print("250 localhost\r\n");
                        else if (line.equals("QUIT")) { output.print("221 bye\r\n"); output.flush(); break; }
                        else output.print("500 test rejected\r\n");
                        output.flush();
                    }
                }
                return commands;
            });
            var env = environment().withProperty("app.email.enabled", "true").withProperty("app.email.host", server.getInetAddress().getHostAddress())
                    .withProperty("app.email.port", Integer.toString(server.getLocalPort())).withProperty("app.email.from", "sender@example.test");
            var sender = new SmtpMemberEmailSender(env, new EmailCrypto(env));
            assertThatThrownBy(() -> sender.send("recipient@example.test", "확인", "전송하면 안 되는 본문")).isInstanceOf(org.springframework.mail.MailException.class);
            assertThat(conversation.get(10, TimeUnit.SECONDS)).noneMatch(command -> command.startsWith("MAIL") || command.startsWith("RCPT") || command.startsWith("DATA"));
        }
    }
}
