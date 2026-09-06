package kr.youthpolicymate.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AdminAccessTest {
    private static final UUID ID = UUID.fromString("10000000-0000-0000-0000-000000000001");

    @Test
    @DisplayName("관리자 설정은 기본 차단하며 등록된 소셜 회원 세션만 허용한다")
    void requiresConfiguredSocialMember() {
        var roles = List.of(new SimpleGrantedAuthority("ROLE_MEMBER"));
        var user = new DefaultOAuth2User(roles, Map.of("memberId", ID.toString()), "memberId");
        var social = new OAuth2AuthenticationToken(user, roles, "kakao");
        assertThat(new AdminAccess(null).authorization().authorize(() -> social, null).isGranted()).isFalse();
        var access = new AdminAccess(Set.of(ID)).authorization();
        assertThat(access.authorize(() -> social, null).isGranted()).isTrue();
        assertThat(access.authorize(() -> UsernamePasswordAuthenticationToken.authenticated(ID.toString(), "", roles), null)
                .isGranted()).isFalse();
        var nonMember = new OAuth2AuthenticationToken(user, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")), "kakao");
        assertThat(access.authorize(() -> nonMember, null).isGranted()).isFalse();
    }
}
