package kr.youthpolicymate.policy.catalog;

import kr.youthpolicymate.policy.ApplicationPeriod;
import tools.jackson.databind.JsonNode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.regex.Pattern;

final class PolicyApplicationPeriod {
    private PolicyApplicationPeriod() {}
    private static final Pattern RANGE = Pattern.compile("([0-9]{8})\\s*~\\s*([0-9]{8})");
    private static final Pattern MENTIONED_DATE = Pattern.compile("(?<![0-9])([0-9]{4})[.년/ -]+([0-9]{1,2})[.월/ -]+([0-9]{1,2})(?![0-9])");

    static ApplicationPeriod parse(JsonNode raw) {
        var period = raw.path("aplyYmd").asString("").strip();
        var periodType = raw.path("aplyPrdSeCd").asString("");
        if (!"0057001".equals(periodType)) {
            if (!period.isEmpty()) return new ApplicationPeriod.Unresolved("신청 기간의 구분과 날짜 안내가 달라요. 공식 공고를 확인해주세요.");
            return switch (periodType) {
                case "0057002" -> new ApplicationPeriod.Rolling();
                case "0057003" -> new ApplicationPeriod.Closed();
                default -> new ApplicationPeriod.Unresolved("접수 기간을 확인할 수 없어요. 공식 공고를 확인해주세요.");
            };
        }
        var matcher = RANGE.matcher(period);
        if (!matcher.matches()) return new ApplicationPeriod.Unresolved("마감일을 확인할 수 없어요. 공식 안내를 확인해주세요.");
        var description = String.join(" ", List.of("plcySprtCn", "plcyAplyMthdCn", "etcMttrCn", "addAplyQlfcCndCn", "srngMthdCn", "plcyExplnCn")
                .stream().map(field -> raw.path(field).asString("")).toList());
        if (description.contains("선착순") || description.contains("소진") || description.contains("회차")) {
            return new ApplicationPeriod.Unresolved("선착순·소진·회차별 접수 안내가 있어 마감일을 추가로 확인해야 해요.");
        }
        try {
            var start = LocalDate.parse(matcher.group(1), DateTimeFormatter.BASIC_ISO_DATE);
            var end = LocalDate.parse(matcher.group(2), DateTimeFormatter.BASIC_ISO_DATE);
            if (end.isBefore(start)) throw new IllegalArgumentException();
            var dates = MENTIONED_DATE.matcher(description);
            while (dates.find()) {
                var mentioned = LocalDate.of(Integer.parseInt(dates.group(1)), Integer.parseInt(dates.group(2)), Integer.parseInt(dates.group(3)));
                if (!mentioned.equals(start) && !mentioned.equals(end)) return new ApplicationPeriod.Unresolved("안내된 날짜가 여러 개여서 마감일을 확인해야 해요.");
            }
            return new ApplicationPeriod.Dates(start, end);
        } catch (java.time.DateTimeException | IllegalArgumentException exception) { return new ApplicationPeriod.Unresolved("제공된 신청 기간을 확인할 수 없어요. 공식 안내를 확인해주세요."); }
    }
}
