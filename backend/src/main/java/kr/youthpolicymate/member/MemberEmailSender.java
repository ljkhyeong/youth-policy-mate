package kr.youthpolicymate.member;

public interface MemberEmailSender {
    boolean available();
    void send(String address, String subject, String body);
}
