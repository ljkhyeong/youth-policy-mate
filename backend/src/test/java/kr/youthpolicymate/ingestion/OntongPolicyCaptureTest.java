package kr.youthpolicymate.ingestion;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.*;

class OntongPolicyCaptureTest {
    private final JsonMapper mapper = JsonMapper.builder().build();
    private final OntongPolicyCapture parser = new OntongPolicyCapture(mapper);

    @Test
    @DisplayName("실제 목록에서 문자열 정책번호·신청기간·분류를 보존하고 공백 사업일을 마감으로 쓰지 않는다")
    void readsObservedList() throws Exception {
        var parsed = parser.parse(fixture());
        var item = parser.item(parsed.items().getFirst());
        assertThat(item.number()).isEqualTo("20260903005400113371");
        assertThat(item.content().category()).isEqualTo("교육･직업훈련");
        assertThat(item.content().applicationPeriod()).isEqualTo("20260701 ~ 20261117");
        assertThat(item.content().regionCodes()).contains("11680");
        assertThat(mapper.readTree(item.rawPolicy()).path("bizPrdEndYmd").asString()).isEqualTo("        ");
    }

    @Test
    @DisplayName("HTTP 성공이어도 업무 오류·상세·깨진 목록은 정상 목록으로 적재하지 않는다")
    void rejectsErrorAndDetail() throws Exception {
        var root = (ObjectNode) mapper.readTree(fixture());
        var body = (ObjectNode) mapper.readTree(root.path("response").path("rawBody").asString());
        body.put("resultCode", 401);
        ((ObjectNode) root.path("response")).put("rawBody", body.toString());
        assertThatThrownBy(() -> parser.parse(root.toString())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> parser.parse(fixture().replace("\"pageType\": \"1\"", "\"pageType\": \"2\"")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> parser.parse("{}" )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("조회수와 수정 시각은 개정을 늘리지 않고 자격 코드 변경은 내용 변경으로 구분한다")
    void separatesSourceAndContent() throws Exception {
        var node = (ObjectNode) parser.parse(fixture()).items().getFirst();
        var before = parser.item(node);
        node.put("inqCnt", "999");
        node.put("lastMdfcnDt", "2026-09-05 12:00:00");
        assertThat(parser.item(node).contentHash()).isEqualTo(before.contentHash());
        node.put("schoolCd", "0049010");
        assertThat(parser.item(node).contentHash()).isNotEqualTo(before.contentHash());
    }

    @Test
    @DisplayName("빈 신청기간은 상시 안내를 유지하고 위험한 링크와 잘못된 정책 식별자를 공개하지 않는다")
    void preservesUnknownsAndSafeLinks() throws Exception {
        var node = (ObjectNode) parser.parse(fixture()).items().getFirst();
        node.put("aplyYmd", ""); node.put("aplyPrdSeCd", "0057002");
        node.putNull("lclsfNm"); node.put("aplyUrlAddr", "javascript:alert(1)");
        node.put("sprtTrgtMinAge", "0"); node.put("sprtTrgtMaxAge", "0");
        var item = parser.item(node);
        assertThat(item.content().applicationPeriod()).startsWith("상시 접수");
        assertThat(item.content().category()).isEmpty();
        assertThat(item.content().links()).noneMatch(link -> link.url().startsWith("javascript:"));
        assertThat(item.content().sections()).anyMatch(section -> section.title().equals("연령 안내")
                && section.text().contains("공식 안내") && !section.text().contains("0세"));
        node.put("plcyNo", 123);
        assertThatThrownBy(() -> parser.item(node)).isInstanceOf(IllegalArgumentException.class);
    }

    private String fixture() throws Exception {
        return Files.readString(Path.of("src/test/resources/ontong/list-capture.json"));
    }
}
