package kr.youthpolicymate.member;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import java.util.Map;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class SocialMemberServiceTest {
    private final MemberIdentityStore store = mock(MemberIdentityStore.class);
    private final SocialMemberService service = new SocialMemberService(store);

    @Test
    @DisplayName("카카오 양력 생년월일만 입력 후보로 제공하고 연락처와 원문 속성은 세션에 복사하지 않는다")
    void keepsOnlyNeededProfile() {
        when(store.login(anyString(), anyString(), anyString())).thenReturn(UUID.randomUUID());
        var account = Map.<String, Object>of("birthday_type", "SOLAR", "birthyear", "2000", "birthday", "0229", "email", "private@example.test");
        var user = service.identify("kakao", Map.of("id", 12345L, "kakao_account", account));
        assertThat(user.getAttributes()).containsEntry("suggestedBirthDate", "2000-02-29")
                .containsOnlyKeys("memberId", "displayName", "suggestedBirthDate");
        assertThat(user.getAuthorities()).extracting(Object::toString).containsExactly("ROLE_MEMBER");
    }

    @Test
    @DisplayName("음력·불완전 날짜와 네이버 생일은 직접 입력으로 남기고 회원 식별자 누락은 거절한다")
    void leavesUnconfirmedBirthdaysEmpty() {
        when(store.login(anyString(), anyString(), anyString())).thenReturn(UUID.randomUUID());
        for (var type : new String[]{"LUNAR", "SOLAR"}) {
            var user = service.identify("kakao", Map.of("id", 123L, "kakao_account", Map.of("birthday_type", type, "birthyear", "2001", "birthday", "0229")));
            assertThat(user.<String>getAttribute("suggestedBirthDate")).isEmpty();
        }
        var naver = service.identify("naver", Map.of("response", Map.of("id", "123", "birthyear", "2000", "birthday", "02-29")));
        assertThat(naver.<String>getAttribute("suggestedBirthDate")).isEmpty();
        assertThatThrownBy(() -> service.identify("naver", Map.of())).isInstanceOf(OAuth2AuthenticationException.class);
    }
}
