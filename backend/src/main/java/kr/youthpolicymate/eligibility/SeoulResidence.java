package kr.youthpolicymate.eligibility;

import java.time.LocalDate;
import java.util.Objects;

// asOfDate는 정보 입력일이 아니라 주민등록상 거주 여부를 나타내는 기준일이다.
public record SeoulResidence(SeoulDistrict district, LocalDate asOfDate) {

    public SeoulResidence {
        district = Objects.requireNonNull(district, "주민등록상 거주하는 서울 자치구가 필요합니다.");
        asOfDate = Objects.requireNonNull(asOfDate, "입력한 거주지의 기준일이 필요합니다.");
    }

    public String description() {
        return asOfDate + " 기준 서울특별시 " + district.label();
    }
}
