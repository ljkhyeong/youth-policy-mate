package kr.youthpolicymate.config;

import kr.youthpolicymate.ingestion.OntongApiClient;
import kr.youthpolicymate.policy.catalog.PolicyApiError;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * 모든 API의 요청 오류를 {code,message}로 응답한다. 캐시 금지는 Spring Security 기본 헤더가 붙인다.
 * IllegalArgumentException 같은 내부 오류는 잡지 않아 500으로 드러나게 한다.
 */
@RestControllerAdvice
class ApiExceptionHandler {
    @ExceptionHandler(ApiException.class)
    ResponseEntity<PolicyApiError> api(ApiException failure) {
        return ResponseEntity.status(failure.status()).body(new PolicyApiError(failure.code(), failure.getMessage()));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, HandlerMethodValidationException.class,
            MethodArgumentTypeMismatchException.class, HttpMessageNotReadableException.class})
    ResponseEntity<PolicyApiError> invalid() { return api(ApiException.invalid()); }

    // 저장한 수집 원본을 다시 해석하지 못한 재처리도 현재 상태와 맞지 않는 요청으로 본다.
    @ExceptionHandler({DuplicateKeyException.class, OntongApiClient.Failure.class})
    ResponseEntity<PolicyApiError> conflict() { return api(ApiException.conflict()); }

    @ExceptionHandler({DataAccessException.class, CannotCreateTransactionException.class})
    ResponseEntity<PolicyApiError> unavailable() {
        return ResponseEntity.status(503).body(new PolicyApiError("SERVICE_UNAVAILABLE", "잠시 처리할 수 없습니다. 다시 시도해주세요."));
    }
}
