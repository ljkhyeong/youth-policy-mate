package kr.youthpolicymate.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import kr.youthpolicymate.member.SocialMemberService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.env.Environment;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;

@Configuration(proxyBeanMethods = false)
@org.springframework.boot.context.properties.EnableConfigurationProperties(AdminAccess.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
class SecurityConfiguration {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, Environment env, AdminAccess adminAccess,
            ObjectProvider<InMemoryClientRegistrationRepository> registrations, ObjectProvider<SocialMemberService> social,
            ObjectProvider<OAuth2AuthorizedClientService> authorizedClients) throws Exception {
        http
                .csrf(csrf -> csrf.ignoringRequestMatchers("/api/v1/policies/checks", "/api/v1/policies/*/evaluation"))
                .requestCache(cache -> cache.disable())
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, exception) -> {
                            var path = request.getRequestURI();
                            response.setStatus(path.startsWith("/api/v1/me/") || path.startsWith("/api/v1/admin/") ? 401 : 403);
                            response.setContentType("application/json;charset=UTF-8");
                            response.getWriter().write("{\"code\":\"LOGIN_REQUIRED\",\"message\":\"로그인이 필요합니다.\"}");
                        })
                        .accessDeniedHandler((request, response, exception) -> {
                            response.setStatus(403); response.setContentType("application/json;charset=UTF-8");
                            response.getWriter().write("{\"code\":\"ACCESS_DENIED\",\"message\":\"요청 권한과 로그인 상태를 확인해주세요.\"}");
                        }))
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers(HttpMethod.GET, "/actuator/health").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/session", "/oauth2/authorization/*", "/login/oauth2/code/*").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/policies", "/api/v1/policies/*", "/api/v1/policies/*/questions").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/policies/checks", "/api/v1/policies/*/evaluation").permitAll()
                        .requestMatchers("/api/v1/me/**").hasRole("MEMBER")
                        .requestMatchers(HttpMethod.GET, "/api/v1/admin/collection-exceptions", "/api/v1/admin/collection-exceptions/*/*")
                            .access(adminAccess.authorization())
                        .anyRequest().denyAll())
                .logout(logout -> logout.logoutUrl("/api/v1/logout").invalidateHttpSession(true)
                        .deleteCookies("YPM_SESSION").logoutSuccessHandler((request, response, authentication) -> response.setStatus(204)));
        var configured = registrations.getIfAvailable();
        if (configured != null && configured.iterator().hasNext()) {
            var frontend = env.getProperty("APP_FRONTEND_URL", "http://127.0.0.1:3000");
            http.oauth2Login(login -> login.userInfoEndpoint(info -> info.userService(social.getObject()))
                    .successHandler((request, response, authentication) -> {
                        var token = (OAuth2AuthenticationToken) authentication;
                        authorizedClients.getObject().removeAuthorizedClient(token.getAuthorizedClientRegistrationId(), token.getName());
                        response.sendRedirect(frontend + "/login/complete");
                    }).failureHandler((request, response, exception) -> response.sendRedirect(frontend + "/login?error=login")));
        }
        return http.build();
    }

    @Bean
    UserDetailsService userDetailsService() {
        // 비밀번호 로그인용 기본 사용자나 임시 계정을 생성하지 않는다.
        return new InMemoryUserDetailsManager();
    }
}
