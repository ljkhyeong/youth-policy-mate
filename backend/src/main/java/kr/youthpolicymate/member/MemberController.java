package kr.youthpolicymate.member;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import kr.youthpolicymate.policy.catalog.BasicConditions;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.UUID;

@RestController
@Profile("!preview")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@RequestMapping("/api/v1")
public class MemberController {
    private final MemberPolicyStore store;
    private final ObjectProvider<InMemoryClientRegistrationRepository> registrations;
    private final Environment environment;
    public MemberController(MemberPolicyStore store, ObjectProvider<InMemoryClientRegistrationRepository> registrations, Environment environment) {
        this.store = store; this.registrations = registrations; this.environment = environment;
    }

    @GetMapping("/session")
    @Operation(operationId = "getMemberSession", summary = "로그인 상태·설정된 로그인 제공자·CSRF 토큰 조회")
    public ResponseEntity<MemberResponses.Session> session(@AuthenticationPrincipal OAuth2User user, @io.swagger.v3.oas.annotations.Parameter(hidden = true) CsrfToken csrf) {
        var providers = new ArrayList<MemberResponses.Provider>();
        var configured = registrations.getIfAvailable();
        var base = environment.getProperty("APP_BACKEND_URL", "http://127.0.0.1:8080");
        if (configured != null) configured.forEach(value -> providers.add(new MemberResponses.Provider(
                value.getRegistrationId(), value.getClientName(), base + "/oauth2/authorization/" + value.getRegistrationId())));
        return privateResponse(new MemberResponses.Session(user != null, user == null ? "" : user.getAttribute("displayName"),
                user == null ? "" : user.getAttribute("suggestedBirthDate"), csrf.getToken(), providers));
    }

    @GetMapping("/me/conditions")
    @Operation(operationId = "getMemberConditions", summary = "내가 저장한 기본 조건 조회")
    public ResponseEntity<MemberResponses.Conditions> conditions(@AuthenticationPrincipal OAuth2User user) { return privateResponse(store.conditions(member(user))); }
    @PutMapping("/me/conditions")
    @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    @Operation(operationId = "saveMemberConditions", summary = "확인한 내 기본 조건 저장")
    public void conditions(@AuthenticationPrincipal OAuth2User user, @RequestBody @Valid BasicConditions input) { store.saveConditions(member(user), input); }
    @DeleteMapping("/me/conditions")
    @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    @Operation(operationId = "clearMemberConditions", summary = "내 저장 조건 삭제")
    public void clear(@AuthenticationPrincipal OAuth2User user) { store.clearConditions(member(user)); }
    @GetMapping("/me/policies")
    @Operation(operationId = "listSavedPolicies", summary = "내 관심 정책과 최신 마감 일정 조회")
    public ResponseEntity<MemberResponses.SavedList> saved(@AuthenticationPrincipal OAuth2User user) { return privateResponse(store.saved(member(user))); }
    @PutMapping("/me/policies/{number}")
    @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    @Operation(operationId = "savePolicy", summary = "내 관심 정책 저장과 마감 알림 예약")
    public void save(@AuthenticationPrincipal OAuth2User user, @PathVariable String number) { store.save(member(user), number); }
    @DeleteMapping("/me/policies/{number}")
    @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    @Operation(operationId = "removeSavedPolicy", summary = "내 관심 정책 해제와 미발송 알림 취소")
    public void remove(@AuthenticationPrincipal OAuth2User user, @PathVariable String number) { store.remove(member(user), number); }
    @GetMapping("/me/notifications")
    @Operation(operationId = "listMemberNotifications", summary = "내 알림 페이지·안 읽은 알림 수 조회",
            description = "최신순으로 정렬하고 필터를 전체 알림에 적용한 뒤 페이지를 나눈다. unreadCount는 필터와 무관한 회원 전체의 안 읽은 알림 수다.")
    public ResponseEntity<MemberResponses.Notifications> notifications(@AuthenticationPrincipal OAuth2User user,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int pageSize,
            @RequestParam(defaultValue = "ALL") MemberResponses.NotificationFilter filter) {
        return privateResponse(store.notifications(member(user), page, pageSize, filter));
    }
    @PostMapping("/me/notifications/{id}/read")
    @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    @Operation(operationId = "readMemberNotification", summary = "내 알림 읽음 처리")
    public void read(@AuthenticationPrincipal OAuth2User user, @PathVariable UUID id) { store.read(member(user), id); }

    static UUID member(OAuth2User user) {
        if (user == null) throw new org.springframework.security.access.AccessDeniedException("로그인이 필요합니다.");
        try { return UUID.fromString(user.getName()); }
        catch (RuntimeException exception) { throw new org.springframework.security.access.AccessDeniedException("로그인을 다시 확인해주세요."); }
    }
    private <T> ResponseEntity<T> privateResponse(T value) { return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(value); }
}
