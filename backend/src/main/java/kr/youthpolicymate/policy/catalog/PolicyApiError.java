package kr.youthpolicymate.policy.catalog;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;

@Schema(requiredProperties = {"code", "message"})
public record PolicyApiError(String code, String message) {
    // 관리자 오류 응답은 권한·운영 정보를 담을 수 있어 저장하지 않게 한다.
    public static ResponseEntity<PolicyApiError> noStore(int status, String code, String message) {
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(new PolicyApiError(code, message));
    }
}
