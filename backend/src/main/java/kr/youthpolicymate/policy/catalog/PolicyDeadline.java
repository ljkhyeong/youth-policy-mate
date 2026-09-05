package kr.youthpolicymate.policy.catalog;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import tools.jackson.databind.JsonNode;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.List;
import java.util.regex.Pattern;

@JsonInclude(JsonInclude.Include.ALWAYS)
@Schema(requiredProperties = {"date", "note"})
public record PolicyDeadline(@Schema(types = {"string", "null"}, format = "date") LocalDate date, String note) {
    private static final Pattern RANGE = Pattern.compile("([0-9]{8})\\s*~\\s*([0-9]{8})");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("uuuuMMdd").withResolverStyle(ResolverStyle.STRICT);

    public static PolicyDeadline from(JsonNode raw) {
        var period = raw.path("aplyYmd").asString("").strip();
        if (!"0057001".equals(raw.path("aplyPrdSeCd").asString(""))) {
            return new PolicyDeadline(null, "상시·마감·미확인 기간은 마감 알림 날짜를 만들지 않아요.");
        }
        var matcher = RANGE.matcher(period);
        if (!matcher.matches()) return new PolicyDeadline(null, "신청기간을 한 개의 날짜 구간으로 확인할 수 없어요.");
        var description = String.join(" ", List.of("plcySprtCn", "plcyAplyMthdCn", "etcMttrCn", "addAplyQlfcCndCn", "srngMthdCn", "plcyExplnCn")
                .stream().map(field -> raw.path(field).asString("")).toList());
        if (description.contains("선착순") || description.contains("소진") || description.contains("회차")) {
            return new PolicyDeadline(null, "선착순·소진·회차별 접수 안내가 있어 마감일을 추가로 확인해야 해요.");
        }
        try {
            var start = LocalDate.parse(matcher.group(1), DATE);
            var end = LocalDate.parse(matcher.group(2), DATE);
            if (end.isBefore(start)) throw new IllegalArgumentException();
            var dates = Pattern.compile("(?<![0-9])([0-9]{4})[.년/ -]+([0-9]{1,2})[.월/ -]+([0-9]{1,2})(?![0-9])").matcher(description);
            while (dates.find()) {
                var mentioned = LocalDate.of(Integer.parseInt(dates.group(1)), Integer.parseInt(dates.group(2)), Integer.parseInt(dates.group(3)));
                if (!mentioned.equals(start) && !mentioned.equals(end)) return new PolicyDeadline(null, "본문에 다른 날짜도 안내되어 마감일을 추가로 확인해야 해요.");
            }
            return new PolicyDeadline(end, "온통청년 신청기간의 종료 날짜예요. 정확한 마감 시각은 공식 안내를 확인해주세요.");
        } catch (RuntimeException exception) { return new PolicyDeadline(null, "신청기간의 날짜가 올바르지 않아요."); }
    }
}
