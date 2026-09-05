package kr.youthpolicymate.ingestion;

import kr.youthpolicymate.policy.catalog.PolicyContent;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.TreeMap;

/** 온통청년 점검 도구의 목록 캡처를 읽는다. 외부 요청과 DB 저장은 하지 않는다. */
public final class OntongPolicyCapture {
    private static final String ENDPOINT = "https://www.youthcenter.go.kr/go/ythip/getPlcy";
    private final ObjectMapper mapper;

    public OntongPolicyCapture(ObjectMapper mapper) { this.mapper = mapper; }

    public Parsed parse(String document) {
        try {
            var capture = mapper.readTree(document);
            var request = capture.path("request");
            var parameters = request.path("parameters");
            if (!ENDPOINT.equals(request.path("endpoint").asString())
                    || !"1".equals(parameters.path("pageType").asString())
                    || !"json".equals(parameters.path("rtnType").asString())
                    || capture.path("response").path("status").asInt() != 200) {
                throw invalid();
            }
            var raw = capture.path("response").path("rawBody");
            if (!raw.isString()) throw invalid();
            var body = mapper.readTree(raw.asString());
            var result = body.path("result");
            var items = result.path("youthPolicyList");
            if (!body.path("resultCode").isInt() || body.path("resultCode").asInt() != 200
                    || !result.path("pagging").isObject() || !items.isArray() || items.size() > 10) throw invalid();
            var capturedAt = Instant.parse(capture.path("capturedAt").asString());
            return new Parsed(capturedAt, hash(capturedAt + "\n" + raw.asString()), items.valueStream().toList());
        } catch (RuntimeException exception) {
            // 원문·인증키·파싱 오류의 입력 내용을 로그로 전파하지 않는다.
            throw invalid();
        }
    }

    public Item item(JsonNode node) {
        var number = text(node, "plcyNo");
        var title = text(node, "plcyNm");
        if (!number.matches("[0-9]{1,100}") || title.isBlank()
                || !"0044002".equals(text(node, "plcyAprvSttsCd"))) throw invalid();
        var sections = new ArrayList<PolicyContent.TextSection>();
        add(sections, "지원 내용", text(node, "plcySprtCn"));
        add(sections, "연령 안내", "연령 제한의 적용 여부·기준일·예외는 공식 안내에서 확인해주세요.");
        add(sections, "추가 신청 자격", text(node, "addAplyQlfcCndCn"));
        add(sections, "참여 제한", text(node, "ptcpPrpTrgtCn"));
        add(sections, "소득 안내", text(node, "earnEtcCn"));
        add(sections, "신청 방법", text(node, "plcyAplyMthdCn"));
        add(sections, "심사 방법", text(node, "srngMthdCn"));
        add(sections, "제출 서류", text(node, "sbmsnDcmntCn"));
        add(sections, "기타 안내", text(node, "etcMttrCn"));
        var links = new ArrayList<PolicyContent.OfficialLink>();
        addLink(links, "공식 신청 안내", text(node, "aplyUrlAddr"));
        addLink(links, "관련 공식 안내", text(node, "refUrlAddr1"));
        addLink(links, "추가 참고 안내", text(node, "refUrlAddr2"));
        var regions = Arrays.stream(text(node, "zipCd").split(",")).map(String::strip)
                .filter(value -> !value.isEmpty()).distinct().toList();
        var period = text(node, "aplyYmd");
        if (period.isEmpty()) {
            period = switch (text(node, "aplyPrdSeCd")) {
                case "0057002" -> "상시 접수 · 종료 조건은 공식 안내 확인";
                case "0057003" -> "마감 · 원문 안내 기준";
                default -> "신청기간 확인 필요";
            };
        }
        var content = new PolicyContent(title, text(node, "plcyExplnCn"), text(node, "lclsfNm"),
                text(node, "sprvsnInstCdNm"), period, sections, links, regions,
                text(node, "lastMdfcnDt"));
        var compared = new TreeMap<String, JsonNode>();
        node.properties().forEach(entry -> {
            if (!List.of("inqCnt", "frstRegDt", "lastMdfcnDt").contains(entry.getKey())) {
                compared.put(entry.getKey(), entry.getValue());
            }
        });
        // 표시 규칙을 바꾸면 버전도 바꿔 같은 원본의 이전 표시 결과를 다시 적용할 수 있게 한다.
        return new Item(number, content, node.toString(), hash("catalog-display-v1\n" + mapper.writeValueAsString(compared)));
    }

    private static String text(JsonNode node, String key) {
        var value = node.path(key);
        if (value.isMissingNode() || value.isNull()) return "";
        if (!value.isString()) throw invalid();
        return value.asString().strip();
    }

    private static void add(List<PolicyContent.TextSection> sections, String title, String text) {
        if (!text.isEmpty()) sections.add(new PolicyContent.TextSection(title, text));
    }

    private static void addLink(List<PolicyContent.OfficialLink> links, String label, String value) {
        try {
            var uri = URI.create(value);
            if (("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                    && uri.getHost() != null && uri.getUserInfo() == null
                    && links.stream().noneMatch(link -> link.url().equals(value))) {
                links.add(new PolicyContent.OfficialLink(label, value));
            }
        } catch (IllegalArgumentException ignored) {
            // 잘못된 링크는 원본에 남기고 클릭 가능한 주소로 공개하지 않는다.
        }
    }

    public static String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("지원하는 정상 목록 캡처 또는 표시 가능한 승인 정책이 아닙니다.");
    }

    public record Parsed(Instant capturedAt, String hash, List<JsonNode> items) {}
    public record Item(String number, PolicyContent content, String rawPolicy, String contentHash) {}
}
