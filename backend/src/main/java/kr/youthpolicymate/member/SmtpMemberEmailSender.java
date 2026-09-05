package kr.youthpolicymate.member;

import jakarta.validation.Validator;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@Profile("!preview")
public class SmtpMemberEmailSender implements MemberEmailSender {
    private final JavaMailSender sender;
    private final boolean enabled;
    private final String from;
    public SmtpMemberEmailSender(Environment env, EmailCrypto crypto, Validator validator,
                                 ObjectProvider<JavaMailSenderImpl> senders) {
        enabled = env.getProperty("app.email.enabled", Boolean.class, false);
        from = env.getProperty("app.email.from", "");
        var configured = senders.getIfAvailable();
        sender = configured;
        if (!enabled) return;
        if (!crypto.ready() || configured == null || !StringUtils.hasText(configured.getHost())
                || !validator.validateValue(MemberEmailAddress.class, "address", from).isEmpty()) {
            throw new IllegalStateException("이메일 활성화에는 암호화 키·SMTP 호스트·발신 주소가 필요합니다.");
        }
        // 기존 설정처럼 사용자명이 있을 때만 SMTP 인증을 사용한다.
        configured.getJavaMailProperties().setProperty("mail.smtp.auth",
                Boolean.toString(StringUtils.hasText(configured.getUsername())));
    }
    @Override public boolean available() { return enabled; }
    @Override public void send(String address, String subject, String body) {
        if (!enabled) throw new IllegalStateException("이메일 발송이 꺼져 있습니다.");
        var message = new SimpleMailMessage();
        message.setFrom(from); message.setTo(address); message.setSubject(subject); message.setText(body);
        sender.send(message);
    }
}
