package kr.youthpolicymate.member;

import kr.youthpolicymate.policy.catalog.PolicyApiError;
import kr.youthpolicymate.policy.catalog.PolicyNotFoundException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = {MemberController.class, MemberEmailController.class, MemberAccountController.class, MemberEmailUnsubscribeController.class})
public class MemberApiExceptionHandler {
    @ExceptionHandler(MemberEmailStore.EmailException.class)
    ResponseEntity<PolicyApiError> email(MemberEmailStore.EmailException failure) {
        return ResponseEntity.status(failure.status).cacheControl(org.springframework.http.CacheControl.noStore())
                .body(new PolicyApiError(failure.code, failure.getMessage()));
    }
    @ExceptionHandler({IllegalArgumentException.class, org.springframework.web.bind.MethodArgumentNotValidException.class,
            org.springframework.http.converter.HttpMessageNotReadableException.class,
            org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,
            org.springframework.web.method.annotation.HandlerMethodValidationException.class})
    ResponseEntity<PolicyApiError> invalid() { return ResponseEntity.badRequest().body(new PolicyApiError("INVALID_MEMBER_INPUT", "입력 내용을 확인해주세요.")); }
    @ExceptionHandler(PolicyNotFoundException.class)
    ResponseEntity<PolicyApiError> missing() { return ResponseEntity.status(404).body(new PolicyApiError("POLICY_NOT_FOUND", "정책을 찾을 수 없습니다.")); }
    @ExceptionHandler(org.springframework.dao.DataAccessException.class)
    ResponseEntity<PolicyApiError> unavailable() { return ResponseEntity.status(503).body(new PolicyApiError("MEMBER_UNAVAILABLE", "내 정보를 잠시 불러올 수 없습니다.")); }
}
