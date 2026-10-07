package kr.youthpolicymate.member;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import kr.youthpolicymate.config.AppUrls;
import kr.youthpolicymate.policy.catalog.BasicConditions;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.UUID;

@RestController
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@RequestMapping("/api/v1")
public class MemberController {
    private final MemberPolicyStore store;
    private final MemberIdentityStore identities;
    private final InMemoryClientRegistrationRepository registrations;
    private final AppUrls urls;
    private final SecurityContextLogoutHandler logout = new SecurityContextLogoutHandler();
    public MemberController(MemberPolicyStore store, MemberIdentityStore identities,
                            InMemoryClientRegistrationRepository registrations, AppUrls urls) {
        this.store = store; this.identities = identities; this.registrations = registrations; this.urls = urls;
    }

    @GetMapping("/session")
    @Operation(operationId = "getMemberSession", summary = "로그인 상태·설정된 로그인 제공자·CSRF 토큰 조회")
    public MemberResponses.Session session(@AuthenticationPrincipal OAuth2User user, @io.swagger.v3.oas.annotations.Parameter(hidden = true) CsrfToken csrf) {
        var providers = new ArrayList<MemberResponses.Provider>();
        registrations.forEach(value -> providers.add(new MemberResponses.Provider(
                value.getRegistrationId(), value.getClientName(), urls.backend() + "/oauth2/authorization/" + value.getRegistrationId())));
        return new MemberResponses.Session(user != null, user == null ? "" : user.getAttribute("displayName"),
                user == null ? "" : user.getAttribute("suggestedBirthDate"), csrf.getToken(), providers);
    }

    @GetMapping("/me/conditions")
    @Operation(operationId = "getMemberConditions", summary = "내가 저장한 기본 조건 조회")
    public MemberResponses.Conditions conditions(@CurrentMember UUID member) { return store.conditions(member); }
    @PutMapping("/me/conditions")
    @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    @Operation(operationId = "saveMemberConditions", summary = "확인한 내 기본 조건 저장")
    public void conditions(@CurrentMember UUID member, @RequestBody @Valid BasicConditions input) { store.saveConditions(member, input); }
    @DeleteMapping("/me/conditions")
    @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    @Operation(operationId = "clearMemberConditions", summary = "내 저장 조건 삭제")
    public void clear(@CurrentMember UUID member) { store.clearConditions(member); }
    @GetMapping("/me/policies")
    @Operation(operationId = "listSavedPolicies", summary = "내 관심 정책과 최신 마감 일정 조회")
    public MemberResponses.SavedList saved(@CurrentMember UUID member) { return store.saved(member); }
    @PutMapping("/me/policies/{number}")
    @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    @Operation(operationId = "savePolicy", summary = "내 관심 정책 저장과 마감 알림 예약")
    public void save(@CurrentMember UUID member, @PathVariable String number) { store.save(member, number); }
    @DeleteMapping("/me/policies/{number}")
    @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    @Operation(operationId = "removeSavedPolicy", summary = "내 관심 정책 해제와 미발송 알림 취소")
    public void remove(@CurrentMember UUID member, @PathVariable String number) { store.remove(member, number); }
    @GetMapping("/me/policies/{number}/changes")
    @Operation(operationId = "getSavedPolicyChanges", summary = "관심 정책의 저장 당시 내용과 현재 내용 조회",
            description = "본인이 저장한 정책만 조회한다. 같은 정책번호의 저장 당시 개정과 현재 공개 개정을 비교하며 저장 기준이나 알림 상태를 변경하지 않는다.")
    public MemberResponses.SavedChanges changes(@CurrentMember UUID member, @PathVariable String number) {
        return store.changes(member, number);
    }
    @GetMapping("/me/notifications")
    @Operation(operationId = "listMemberNotifications", summary = "내 알림 페이지·안 읽은 알림 수 조회",
            description = "최신순으로 정렬하고 필터를 전체 알림에 적용한 뒤 페이지를 나눈다. unreadCount는 필터와 무관한 회원 전체의 안 읽은 알림 수다.")
    public MemberResponses.Notifications notifications(@CurrentMember UUID member,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int pageSize,
            @RequestParam(defaultValue = "ALL") MemberResponses.NotificationFilter filter) {
        return store.notifications(member, page, pageSize, filter);
    }
    @PostMapping("/me/notifications/{id}/read")
    @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    @Operation(operationId = "readMemberNotification", summary = "내 알림 읽음 처리")
    public void read(@CurrentMember UUID member, @PathVariable UUID id) { store.read(member, id); }
    @PostMapping("/me/notifications/read-all")
    @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    @Operation(operationId = "readAllMemberNotifications", summary = "내 알림 모두 읽음 처리",
            description = "페이지·필터와 관계없이 본인의 미읽음 알림 전체를 처리한다. 갱신 쿼리 시작 후 도착한 알림과 기존 읽은 시각은 유지한다.")
    public void readAll(@CurrentMember UUID member) { store.readAll(member); }
    @DeleteMapping("/me/account")
    @Operation(operationId = "withdrawMember", summary = "회원 탈퇴와 개인 데이터·모든 로그인 세션 삭제")
    @ApiResponse(responseCode = "204", description = "탈퇴 완료")
    @ApiResponse(responseCode = "503", description = "저장소 오류로 탈퇴 미완료")
    public ResponseEntity<Void> withdraw(@CurrentMember UUID member, Authentication authentication,
            HttpServletRequest request, HttpServletResponse response) {
        identities.withdraw(member);
        logout.logout(request, response, authentication);
        return ResponseEntity.noContent().build();
    }
}
