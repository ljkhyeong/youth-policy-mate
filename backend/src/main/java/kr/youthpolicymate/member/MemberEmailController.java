package kr.youthpolicymate.member;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Profile;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.*;

@RestController
@Profile("!preview")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@RequestMapping("/api/v1/me")
public class MemberEmailController {
    private final MemberEmailStore store;
    public MemberEmailController(MemberEmailStore store) { this.store = store; }
    @GetMapping("/email-settings") @Operation(operationId = "getMemberEmailSettings", summary = "내 이메일 확인과 수신 설정 조회")
    ResponseEntity<MemberEmailStore.Settings> settings(@AuthenticationPrincipal OAuth2User user) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(store.settings(MemberController.member(user)));
    }
    @PostMapping("/email-verification") @ResponseStatus(HttpStatus.NO_CONTENT) @Operation(operationId = "requestMemberEmailVerification", summary = "이메일 확인 코드 요청")
    void request(@AuthenticationPrincipal OAuth2User user, @Valid @RequestBody MemberEmailAddress input) { store.request(MemberController.member(user), input.address()); }
    @PostMapping("/email-verification/confirm") @ResponseStatus(HttpStatus.NO_CONTENT) @Operation(operationId = "confirmMemberEmailVerification", summary = "이메일 확인 코드 비교")
    void confirm(@AuthenticationPrincipal OAuth2User user, @Valid @RequestBody Code input) {
        if (!store.confirm(MemberController.member(user), input.code()))
            throw new MemberEmailStore.EmailException(400, "EMAIL_CODE_INVALID", "확인 코드가 틀렸거나 만료됐어요. 5회 실패했다면 새 코드를 요청해주세요.");
    }
    @PutMapping("/email-settings") @ResponseStatus(HttpStatus.NO_CONTENT) @Operation(operationId = "changeMemberEmailConsent", summary = "이메일 알림 별도 수신 동의 또는 해제")
    void consent(@AuthenticationPrincipal OAuth2User user, @Valid @RequestBody Consent input) { store.consent(MemberController.member(user), input.enabled()); }
    @DeleteMapping("/email-settings") @ResponseStatus(HttpStatus.NO_CONTENT) @Operation(operationId = "deleteMemberEmailAddress", summary = "내 이메일 주소와 미발송 요청 삭제")
    void remove(@AuthenticationPrincipal OAuth2User user) { store.remove(MemberController.member(user)); }
    @Schema(name = "MemberEmailCode", requiredProperties = {"code"})
    public record Code(@NotNull @Size(max = 100) String code) {}
    @Schema(name = "MemberEmailConsent", requiredProperties = {"enabled"})
    public record Consent(@NotNull Boolean enabled) {}
}
