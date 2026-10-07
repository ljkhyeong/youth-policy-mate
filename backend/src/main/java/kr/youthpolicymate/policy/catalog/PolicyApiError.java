package kr.youthpolicymate.policy.catalog;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Schema(requiredProperties = {"code", "message"})
public record PolicyApiError(String code, String message) {
    /** 보안 진입점·필터처럼 MVC 밖에서 같은 오류 본문을 쓴다. 쓰고 나면 응답이 커밋되므로 헤더는 먼저 설정한다. */
    public void writeTo(HttpServletResponse response, int status) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8);
        JsonMapper.shared().writeValue(response.getOutputStream(), this);
    }
}
