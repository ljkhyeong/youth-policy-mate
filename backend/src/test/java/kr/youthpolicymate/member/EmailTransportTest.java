package kr.youthpolicymate.member;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.mail.autoconfigure.MailSenderAutoConfiguration;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mail.javamail.JavaMailSenderImpl;
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
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withConfiguration(AutoConfigurations.of(MailSenderAutoConfiguration.class, ValidationAutoConfiguration.class))
            .withUserConfiguration(EmailCrypto.class, SmtpMemberEmailSender.class)
            .withPropertyValues("app.email.enabled=false",
                    "app.email.encryption-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=");

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
    }

    @Test
    @DisplayName("메일 설정이 없으면 비활성 상태로 실행하고 불완전한 설정으로 활성화하면 시작을 거절한다")
    void requiresSettingsOnlyWhenEnabled() {
        contextRunner.run(context -> assertThat(context.getBean(SmtpMemberEmailSender.class).available()).isFalse());
        contextRunner.withPropertyValues("app.email.enabled=true").run(context -> assertThat(context).hasFailed());
        contextRunner.withPropertyValues("app.email.enabled=true", "app.email.host=localhost",
                "app.email.from=first..last@example.test").run(context -> assertThat(context).hasFailed());
    }

    @Test
    @DisplayName("기존 SMTP 설정을 Boot 메일 빈에 연결하고 인증·TLS·타임아웃을 유지한다")
    void bindsMailSettings() {
        contextRunner.withPropertyValues("app.email.enabled=true", "EMAIL_SMTP_HOST=localhost", "EMAIL_SMTP_PORT=2525",
                "EMAIL_SMTP_USERNAME=test-user", "EMAIL_SMTP_PASSWORD=test-password", "EMAIL_FROM=sender@example.test")
                .run(context -> {
                    var mail = context.getBean(JavaMailSenderImpl.class);
                    assertThat(context.getBean(SmtpMemberEmailSender.class).available()).isTrue();
                    assertThat(mail.getHost()).isEqualTo("localhost");
                    assertThat(mail.getPort()).isEqualTo(2525);
                    assertThat(mail.getUsername()).isEqualTo("test-user");
                    assertThat(mail.getPassword()).isEqualTo("test-password");
                    assertThat(mail.getDefaultEncoding()).isEqualTo("UTF-8");
                    assertThat(mail.getJavaMailProperties()).containsEntry("mail.smtp.auth", "true")
                            .containsEntry("mail.smtp.starttls.enable", "true")
                            .containsEntry("mail.smtp.starttls.required", "true")
                            .containsEntry("mail.smtp.ssl.checkserveridentity", "true")
                            .containsEntry("mail.smtp.connectiontimeout", "5000")
                            .containsEntry("mail.smtp.timeout", "5000")
                            .containsEntry("mail.smtp.writetimeout", "5000");
                });
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
            contextRunner.withPropertyValues("app.email.enabled=true", "app.email.host=" + server.getInetAddress().getHostAddress(),
                    "app.email.port=" + server.getLocalPort(), "app.email.from=sender@example.test").run(context -> {
                var sender = context.getBean(SmtpMemberEmailSender.class);
                assertThat(context.getBean(JavaMailSenderImpl.class).getJavaMailProperties()).containsEntry("mail.smtp.auth", "false");
                assertThatThrownBy(() -> sender.send(java.util.UUID.randomUUID(), "recipient@example.test", "확인", "전송하면 안 되는 본문"))
                        .isInstanceOf(org.springframework.mail.MailException.class);
            });
            assertThat(conversation.get(10, TimeUnit.SECONDS)).noneMatch(command -> command.startsWith("MAIL") || command.startsWith("RCPT") || command.startsWith("DATA"));
        }
    }
}
