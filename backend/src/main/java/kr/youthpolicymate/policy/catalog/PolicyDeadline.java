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
        var periodType = raw.path("aplyPrdSeCd").asString("");
        if (!"0057001".equals(periodType)) {
            return new PolicyDeadline(null, switch (periodType) {
                case "0057002" -> "상시 접수라 마감 알림을 제공하지 않아요.";
                case "0057003" -> "온통청년 안내 기준으로 접수가 끝났어요.";
                default -> "마감일을 확인할 수 없어 알림을 예약하지 못했어요. 공식 안내를 확인해주세요.";
            });
        }
        var matcher = RANGE.matcher(period);
        if (!matcher.matches()) return new PolicyDeadline(null, "마감일을 확인할 수 없어요. 공식 안내를 확인해주세요.");
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
                if (!mentioned.equals(start) && !mentioned.equals(end)) return new PolicyDeadline(null, "안내된 날짜가 여러 개여서 마감일을 확인해야 해요.");
            }
            return new PolicyDeadline(end, "온통청년에 안내된 마감일이에요. 마감 시간은 공식 안내를 확인해주세요.");
        } catch (RuntimeException exception) { return new PolicyDeadline(null, "제공된 신청 기간을 확인할 수 없어요. 공식 안내를 확인해주세요."); }
    }
}
