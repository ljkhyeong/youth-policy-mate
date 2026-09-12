package kr.youthpolicymate.member;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.dao.DataAccessException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/** 탈퇴와 동시에 완료된 OAuth 콜백도 삭제된 회원의 권한을 되살리지 못하게 한다. */
public class MemberSessionFilter extends OncePerRequestFilter {
    private final MemberIdentityStore identities;
    private final SecurityContextLogoutHandler logout = new SecurityContextLogoutHandler();

    public MemberSessionFilter(MemberIdentityStore identities) { this.identities = identities; }

    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
        var path = request.getRequestURI();
        return !path.equals("/api/v1/session") && !path.startsWith("/api/v1/me/") && !path.startsWith("/api/v1/admin/");
    }

    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof OAuth2AuthenticationToken token) {
            boolean exists;
            try { exists = identities.exists(UUID.fromString(token.getName())); }
            catch (IllegalArgumentException invalid) { exists = false; }
            catch (DataAccessException unavailable) {
                response.setStatus(503);
                response.setHeader("Cache-Control", "no-store");
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"code\":\"MEMBER_UNAVAILABLE\",\"message\":\"로그인 상태를 잠시 확인할 수 없습니다.\"}");
                return;
            }
            if (!exists) logout.logout(request, response, authentication);
        }
        chain.doFilter(request, response);
    }
}
