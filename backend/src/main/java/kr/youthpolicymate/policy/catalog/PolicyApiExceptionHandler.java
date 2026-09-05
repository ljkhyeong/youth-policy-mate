package kr.youthpolicymate.policy.catalog;

import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(assignableTypes = PolicyCatalogController.class)
class PolicyApiExceptionHandler {
    @ExceptionHandler(PolicyNotFoundException.class)
    ResponseEntity<PolicyApiError> missing() {
        return ResponseEntity.status(404).body(new PolicyApiError("POLICY_NOT_FOUND", "정책을 찾을 수 없습니다."));
    }

    @ExceptionHandler({HandlerMethodValidationException.class, MethodArgumentTypeMismatchException.class})
    ResponseEntity<PolicyApiError> invalid() {
        return ResponseEntity.badRequest().body(new PolicyApiError("INVALID_SEARCH", "검색어와 페이지 조건을 확인해주세요."));
    }

    @ExceptionHandler(DataAccessException.class)
    ResponseEntity<PolicyApiError> unavailable() {
        return ResponseEntity.status(503).body(new PolicyApiError("POLICY_UNAVAILABLE", "정책 정보를 잠시 불러올 수 없습니다."));
    }
}
