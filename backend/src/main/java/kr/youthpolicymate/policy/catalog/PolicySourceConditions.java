package kr.youthpolicymate.policy.catalog;

import tools.jackson.databind.JsonNode;

import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 온통청년 원본의 구조화 조건 표기. 자격 판정이 아니며 제한을 표기한 값만 읽는다.
 * 무관·제한없음 표기는 조건이 없다는 근거가 아니므로 담지 않는다. 기타·정의서에 없는 코드는 대상을 좁혀 보이지 않게 공식 안내 확인으로 남긴다.
 */
record PolicySourceConditions(Integer minimumAge, Integer maximumAge, Integer maximumAnnualIncome,
                              Targets employment, Targets education) {
    private static final String OTHER = "조건 표기 있음 · 공식 안내 확인";
    private static final Map<String, String> EMPLOYMENT = Map.of("0013001", "재직자", "0013002", "자영업자", "0013003", "미취업자",
            "0013004", "프리랜서", "0013005", "일용근로자", "0013006", "(예비)창업자", "0013007", "단기근로자", "0013008", "영농종사자");
    private static final Map<String, String> EDUCATION = Map.of("0049005", "대학 재학");
    private static final String EMPLOYMENT_UNRESTRICTED = "0013010";
    private static final String EDUCATION_UNRESTRICTED = "0049010";

    static PolicySourceConditions from(JsonNode raw) {
        Integer minimum = null, maximum = null;
        // 연령 제한 여부가 Y인 표기는 대부분 0~0이라 제한 없음으로 보고, 함께 남은 범위는 해석하지 않는다.
        if ("N".equals(text(raw, "sprtTrgtAgeLmtYn"))) {
            minimum = positive(raw.path("sprtTrgtMinAge"));
            maximum = positive(raw.path("sprtTrgtMaxAge"));
            if (minimum != null && maximum != null && minimum > maximum) minimum = maximum = null;
        }
        var income = "0043002".equals(text(raw, "earnCndSeCd")) ? positive(raw.path("earnMaxAmt")) : null;
        return new PolicySourceConditions(minimum, maximum, income, Targets.of(codes(raw, "jobCd"), EMPLOYMENT, EMPLOYMENT_UNRESTRICTED),
                Targets.of(codes(raw, "schoolCd"), EDUCATION, EDUCATION_UNRESTRICTED));
    }

    /** 정의서로 확인한 대상 이름과, 기타·모르는 코드가 함께 표기됐는지. */
    record Targets(List<String> known, boolean other) {
        static Targets of(List<String> codes, Map<String, String> labels, String unrestricted) {
            if (codes.contains(unrestricted)) return new Targets(List.of(), false);
            return new Targets(codes.stream().map(labels::get).filter(Objects::nonNull).toList(),
                    codes.stream().anyMatch(code -> !labels.containsKey(code)));
        }
        Optional<String> text() {
            if (known.isEmpty()) return other ? Optional.of(OTHER) : Optional.empty();
            return Optional.of(String.join("·", known) + (other ? " 등 · 공식 안내 확인" : ""));
        }
    }

    List<PolicySourceCondition> items() {
        var items = new ArrayList<PolicySourceCondition>();
        ageText().ifPresent(text -> items.add(new PolicySourceCondition("연령", text)));
        if (maximumAnnualIncome != null) items.add(new PolicySourceCondition("소득", "연소득 %,d만 원 이하".formatted(maximumAnnualIncome)));
        employment.text().ifPresent(text -> items.add(new PolicySourceCondition("취업 상태", text)));
        education.text().ifPresent(text -> items.add(new PolicySourceCondition("학력", text)));
        return List.copyOf(items);
    }

    Optional<String> ageText() {
        if (minimumAge != null && maximumAge != null) return Optional.of("만 " + minimumAge + "~" + maximumAge + "세");
        if (maximumAge != null) return Optional.of("만 " + maximumAge + "세 이하");
        if (minimumAge != null) return Optional.of("만 " + minimumAge + "세 이상");
        return Optional.empty();
    }

    static int completedYears(LocalDate birth, LocalDate today) { return Period.between(birth, today).getYears(); }

    private static Integer positive(JsonNode node) {
        Integer value = node.isIntegralNumber() ? Integer.valueOf(node.asInt())
                : node.isString() && node.asString().strip().matches("[0-9]{1,7}") ? Integer.valueOf(node.asString().strip()) : null;
        return value != null && value > 0 ? value : null;
    }

    private static String text(JsonNode raw, String field) {
        var value = raw.path(field);
        return value.isString() ? value.asString().strip() : "";
    }

    private static List<String> codes(JsonNode raw, String field) {
        return Arrays.stream(text(raw, field).split(",")).map(String::strip).filter(code -> !code.isEmpty()).toList();
    }
}
