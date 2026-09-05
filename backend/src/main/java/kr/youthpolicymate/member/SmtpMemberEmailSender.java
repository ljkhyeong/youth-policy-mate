package kr.youthpolicymate.member;

import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Component;

@Component
@Profile("!preview")
public class SmtpMemberEmailSender implements MemberEmailSender {
    private final JavaMailSenderImpl sender = new JavaMailSenderImpl();
    private final boolean enabled;
    private final String from;
    public SmtpMemberEmailSender(Environment env, EmailCrypto crypto) {
        enabled = env.getProperty("app.email.enabled", Boolean.class, false);
        from = env.getProperty("app.email.from", "");
        String host = env.getProperty("app.email.host", "");
        if (!enabled) return;
        if (!crypto.ready() || host.isBlank() || !MemberEmailStore.validAddress(from)) {
            throw new IllegalStateException("이메일 활성화에는 암호화 키·SMTP 호스트·발신 주소가 필요합니다.");
        }
        sender.setHost(host);
        sender.setPort(env.getProperty("app.email.port", Integer.class, 587));
        sender.setUsername(env.getProperty("app.email.username", ""));
        sender.setPassword(env.getProperty("app.email.password", ""));
        sender.setDefaultEncoding("UTF-8");
        var properties = sender.getJavaMailProperties();
        properties.setProperty("mail.smtp.auth", Boolean.toString(!sender.getUsername().isBlank()));
        properties.setProperty("mail.smtp.starttls.enable", "true");
        properties.setProperty("mail.smtp.starttls.required", "true");
        properties.setProperty("mail.smtp.ssl.checkserveridentity", "true");
        properties.setProperty("mail.smtp.connectiontimeout", "5000");
        properties.setProperty("mail.smtp.timeout", "5000");
        properties.setProperty("mail.smtp.writetimeout", "5000");
    }
    @Override public boolean available() { return enabled; }
    @Override public void send(String address, String subject, String body) {
        if (!enabled) throw new IllegalStateException("이메일 발송이 꺼져 있습니다.");
        var message = new SimpleMailMessage();
        message.setFrom(from); message.setTo(address); message.setSubject(subject); message.setText(body);
        sender.send(message);
    }
}
