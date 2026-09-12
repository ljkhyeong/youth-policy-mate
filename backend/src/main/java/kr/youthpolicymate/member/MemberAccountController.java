package kr.youthpolicymate.member;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Profile;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("!preview")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class MemberAccountController {
    private final MemberAccountService accounts;
    private final SecurityContextLogoutHandler logout = new SecurityContextLogoutHandler();

    public MemberAccountController(MemberAccountService accounts) { this.accounts = accounts; }

    @DeleteMapping("/api/v1/me/account")
    @Operation(operationId = "withdrawMember", summary = "회원 탈퇴와 개인 데이터·모든 로그인 세션 삭제")
    @ApiResponse(responseCode = "204", description = "탈퇴 완료")
    @ApiResponse(responseCode = "503", description = "저장소 오류로 탈퇴 미완료")
    public ResponseEntity<Void> withdraw(@AuthenticationPrincipal OAuth2User user, Authentication authentication,
            HttpServletRequest request, HttpServletResponse response) {
        accounts.withdraw(MemberController.member(user));
        logout.logout(request, response, authentication);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
}
