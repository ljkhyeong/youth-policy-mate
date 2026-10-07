package kr.youthpolicymate.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/** 설정한 회원 ID의 소셜 로그인 세션만 관리자 API를 허용한다. 요청마다 판정한다. */
@ConfigurationProperties("app.admin")
record AdminAccess(Set<UUID> memberIds) implements AuthorizationManager<RequestAuthorizationContext> {
    AdminAccess {
        memberIds = memberIds == null ? Set.of() : Set.copyOf(memberIds);
    }

    @Override
    public AuthorizationResult authorize(Supplier<? extends Authentication> authentication, RequestAuthorizationContext context) {
        var principal = authentication.get();
        return new AuthorizationDecision(principal instanceof OAuth2AuthenticationToken
                && principal.isAuthenticated()
                && principal.getAuthorities().stream().anyMatch(role -> role.getAuthority().equals("ROLE_MEMBER"))
                && memberIds.stream().anyMatch(id -> id.toString().equals(principal.getName())));
    }

    // Spring Security가 TRACE 로그에 관리자를 출력하므로 회원 ID를 남기지 않는다.
    @Override
    public String toString() { return "AdminAccess[관리자 " + memberIds.size() + "명]"; }
}
