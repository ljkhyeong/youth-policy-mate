package kr.youthpolicymate.admin;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;
import java.util.Map;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;

/** 관리자 API 테스트가 함께 쓰는 관리자·일반 회원 픽스처와 소셜 로그인 세션. */
public final class AdminTestSupport {
    public static final String ADMIN = "10000000-0000-0000-0000-000000000001";
    public static final String MEMBER = "20000000-0000-0000-0000-000000000002";

    private AdminTestSupport() {}

    public static RequestPostProcessor social(String memberId) {
        return oauth2Login().oauth2User(new DefaultOAuth2User(List.of(new SimpleGrantedAuthority("ROLE_MEMBER")),
                Map.of("memberId", memberId), "memberId"));
    }

    public static void insertMembers(JdbcClient jdbc) {
        jdbc.sql("""
                INSERT INTO members(id, provider, provider_subject, display_name) VALUES
                ('10000000-0000-0000-0000-000000000001', 'kakao', 'admin-fixture', '검증 관리자'),
                ('20000000-0000-0000-0000-000000000002', 'naver', 'member-fixture', '검증 회원')
                ON CONFLICT (id) DO NOTHING
                """).update();
    }
}
