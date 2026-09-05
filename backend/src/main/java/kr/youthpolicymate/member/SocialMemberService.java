package kr.youthpolicymate.member;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class SocialMemberService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {
    private final MemberIdentityStore identities;
    private final DefaultOAuth2UserService delegate = new DefaultOAuth2UserService();
    public SocialMemberService(MemberIdentityStore identities) { this.identities = identities; }

    @Override public OAuth2User loadUser(OAuth2UserRequest request) {
        try { return identify(request.getClientRegistration().getRegistrationId(), delegate.loadUser(request).getAttributes()); }
        catch (RuntimeException exception) { throw invalid(); }
    }

    OAuth2User identify(String provider, Map<String, Object> attributes) {
        Map<?, ?> profile;
        String subject;
        String name;
        String birthday = "";
        if (provider.equals("kakao")) {
            if (!(attributes.get("id") instanceof Number id)) throw invalid();
            subject = id.toString();
            if (!subject.matches("[1-9][0-9]*")) throw invalid();
            profile = attributes.get("kakao_account") instanceof Map<?, ?> account ? account : Map.of();
            var details = profile.get("profile") instanceof Map<?, ?> map ? map : Map.of();
            name = string(details.get("nickname"));
            if ("SOLAR".equals(profile.get("birthday_type"))) birthday = date(profile.get("birthyear"), profile.get("birthday"));
        } else if (provider.equals("naver")) {
            profile = attributes.get("response") instanceof Map<?, ?> map ? map : Map.of();
            subject = string(profile.get("id"));
            name = string(profile.get("nickname"));
            // 네이버 응답에는 음력 여부가 없으므로 생일을 자동 확정하지 않는다.
        } else throw invalid();
        if (subject.isBlank() || subject.length() > 255) throw invalid();
        name = name.isBlank() ? "회원" : name.substring(0, Math.min(name.length(), 80));
        var memberId = identities.login(provider, subject, name);
        var safe = new LinkedHashMap<String, Object>();
        safe.put("memberId", memberId.toString());
        safe.put("displayName", name);
        safe.put("suggestedBirthDate", birthday);
        // 로그인에 필요하지 않은 연락처·프로필 원문·외부 토큰은 세션 속성에 복사하지 않는다.
        return new DefaultOAuth2User(List.of(new SimpleGrantedAuthority("ROLE_MEMBER")), safe, "memberId");
    }

    private String date(Object year, Object birthday) {
        var y = string(year); var d = string(birthday);
        if (!y.matches("[0-9]{4}") || !d.matches("[0-9]{4}")) return "";
        try { return LocalDate.parse(y + "-" + d.substring(0,2) + "-" + d.substring(2)).toString(); }
        catch (RuntimeException ignored) { return ""; }
    }
    private String string(Object value) { return value instanceof String text ? text : ""; }
    private OAuth2AuthenticationException invalid() { return new OAuth2AuthenticationException(new OAuth2Error("social_login_failed")); }
}
