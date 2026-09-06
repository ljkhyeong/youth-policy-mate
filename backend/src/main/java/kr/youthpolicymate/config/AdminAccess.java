package kr.youthpolicymate.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

import java.util.Set;
import java.util.UUID;

@ConfigurationProperties("app.admin")
record AdminAccess(Set<UUID> memberIds) {
    AdminAccess {
        memberIds = memberIds == null ? Set.of() : Set.copyOf(memberIds);
    }

    AuthorizationManager<RequestAuthorizationContext> authorization() {
        return (authentication, context) -> {
            var principal = authentication.get();
            return new AuthorizationDecision(principal instanceof OAuth2AuthenticationToken
                    && principal.isAuthenticated()
                    && principal.getAuthorities().stream().anyMatch(role -> role.getAuthority().equals("ROLE_MEMBER"))
                    && memberIds.stream().anyMatch(id -> id.toString().equals(principal.getName())));
        };
    }
}
