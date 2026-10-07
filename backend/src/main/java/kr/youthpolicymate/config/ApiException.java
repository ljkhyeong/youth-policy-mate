package kr.youthpolicymate.config;

import org.springframework.http.HttpStatus;

/**
 * {@link ApiExceptionHandler}가 {code,message} 본문으로 바꾸는 요청 오류.
 * 화면이 코드로 분기하는 이메일 오류만 별도 코드를 쓰고 나머지는 상태별 공통 코드를 쓴다.
 */
public class ApiException extends RuntimeException {
    private final HttpStatus status;
    private final String code;

    public ApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public static ApiException invalid() {
        return new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "입력 내용을 확인해주세요.");
    }

    public static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "요청한 항목을 찾을 수 없습니다.");
    }

    public static ApiException conflict() {
        return new ApiException(HttpStatus.CONFLICT, "CONFLICT", "내용이 바뀌었습니다. 최신 내용을 다시 확인해주세요.");
    }

    public HttpStatus status() { return status; }
    public String code() { return code; }
}
