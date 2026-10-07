package kr.youthpolicymate.member;

import kr.youthpolicymate.config.AppUrls;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class MemberConfiguration {
    @Bean
    SocialMemberService socialMemberService(MemberIdentityStore store) { return new SocialMemberService(store); }

    @Bean
    InMemoryClientRegistrationRepository clientRegistrationRepository(Environment env, AppUrls urls) {
        var registrations = new LinkedHashMap<String, ClientRegistration>();
        var base = urls.backend();
        add(registrations, env, "kakao", "카카오", base, "https://kauth.kakao.com/oauth/authorize",
                "https://kauth.kakao.com/oauth/token", "https://kapi.kakao.com/v2/user/me", "id");
        add(registrations, env, "naver", "네이버", base, "https://nid.naver.com/oauth2.0/authorize",
                "https://nid.naver.com/oauth2.0/token", "https://openapi.naver.com/v1/nid/me", "response");
        return new InMemoryClientRegistrationRepository(Collections.unmodifiableMap(registrations));
    }

    private void add(Map<String, ClientRegistration> target, Environment env, String id, String name, String base,
                     String authorizationUri, String tokenUri, String userInfoUri, String userName) {
        var key = env.getProperty(id.toUpperCase(java.util.Locale.ROOT) + "_CLIENT_ID", "");
        var secret = env.getProperty(id.toUpperCase(java.util.Locale.ROOT) + "_CLIENT_SECRET", "");
        if (key.isBlank() || secret.isBlank()) return;
        target.put(id, ClientRegistration.withRegistrationId(id).clientName(name).clientId(key).clientSecret(secret)
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri(base + "/login/oauth2/code/" + id).authorizationUri(authorizationUri).tokenUri(tokenUri)
                .userInfoUri(userInfoUri).userNameAttributeName(userName).build());
    }
}
