package kr.youthpolicymate.member;

public interface MemberEmailSender {
    boolean available();
    String provider();
    String send(java.util.UUID requestId, String address, String subject, String body);
}
