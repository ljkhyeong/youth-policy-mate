package kr.youthpolicymate.config;

import jakarta.servlet.DispatcherType;
import kr.youthpolicymate.member.MemberIdentityStore;
import kr.youthpolicymate.member.MemberSessionFilter;
import kr.youthpolicymate.member.SocialMemberService;
import kr.youthpolicymate.policy.catalog.PolicyApiError;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.header.HeaderWriterFilter;

/** 캐시 금지는 Spring Security 기본 헤더(Cache-Control no-store 포함)가 모든 응답에 붙이므로 headers()를 끄지 않는다. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
class SecurityConfiguration {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, AppUrls urls, AdminAccess adminAccess,
            InMemoryClientRegistrationRepository registrations, SocialMemberService social,
            OAuth2AuthorizedClientService authorizedClients, MemberIdentityStore identities) throws Exception {
        http
                .csrf(csrf -> csrf.ignoringRequestMatchers("/api/v1/email-unsubscribe/*", "/api/v1/webhooks/resend", "/api/v1/policies/checks", "/api/v1/policies/*/evaluation", "/api/v1/policies/*/question-prefill"))
                .requestCache(cache -> cache.disable())
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, exception) -> {
                            var path = request.getRequestURI();
                            new PolicyApiError("LOGIN_REQUIRED", "로그인이 필요합니다.")
                                    .writeTo(response, path.startsWith("/api/v1/me/") || path.startsWith("/api/v1/admin/") ? 401 : 403);
                        })
                        .accessDeniedHandler((request, response, exception) ->
                                new PolicyApiError("ACCESS_DENIED", "요청 권한과 로그인 상태를 확인해주세요.").writeTo(response, 403)))
                .authorizeHttpRequests(requests -> requests
                        // 처리하지 못한 예외의 오류 응답(/error 재디스패치)이 403으로 바뀌지 않게 한다.
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/health/liveness", "/actuator/health/readiness").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/webhooks/resend").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/email-unsubscribe/*").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/email-unsubscribe/*").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/session", "/oauth2/authorization/*", "/login/oauth2/code/*").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/policies", "/api/v1/policies/*", "/api/v1/policies/*/questions").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/policies/checks", "/api/v1/policies/*/evaluation", "/api/v1/policies/*/question-prefill").permitAll()
                        .requestMatchers("/api/v1/me/**").hasRole("MEMBER")
                        .requestMatchers("/api/v1/admin/**").access(adminAccess)
                        .anyRequest().denyAll())
                .logout(logout -> logout.logoutUrl("/api/v1/logout")
                        .logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT)));
        if (registrations.iterator().hasNext()) {
            http.oauth2Login(login -> login.userInfoEndpoint(info -> info.userService(social))
                    .successHandler((request, response, authentication) -> {
                        var token = (OAuth2AuthenticationToken) authentication;
                        authorizedClients.removeAuthorizedClient(token.getAuthorizedClientRegistrationId(), token.getName());
                        response.sendRedirect(urls.frontend() + "/login/complete");
                    }).failureHandler((request, response, exception) -> response.sendRedirect(urls.frontend() + "/login?error=login")));
        }
        // 인증 정보를 불러오고 기본 보안 헤더 작성기가 응답을 감싼 뒤, CSRF·로그아웃 처리 전에 탈퇴한 회원의 세션을 끊는다.
        http.addFilterAfter(new MemberSessionFilter(identities), HeaderWriterFilter.class);
        return http.build();
    }
}
