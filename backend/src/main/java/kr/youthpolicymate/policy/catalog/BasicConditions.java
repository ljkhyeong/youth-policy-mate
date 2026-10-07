package kr.youthpolicymate.policy.catalog;

import io.swagger.v3.oas.annotations.media.Schema;
import kr.youthpolicymate.eligibility.SeoulDistrict;

import java.time.LocalDate;

public record BasicConditions(@Schema(types = {"string", "null"}, format = "date") LocalDate birthDate,
                              @Schema(types = {"string", "null"}) SeoulDistrict district,
                              @Schema(types = {"string", "null"}) EmploymentStatus employmentStatus) {
    public enum EmploymentStatus {
        EMPLOYED, SELF_EMPLOYED, NOT_EMPLOYED, FREELANCER, DAY_WORKER,
        ENTREPRENEUR, SHORT_TERM_WORKER, FARMER, OTHER
    }

    // 서울 날짜 기준 미래이거나 1년 이전인 생년월일을 거절한다. 시각은 호출부가 한 번만 읽어 넘긴다.
    public static void checkBirthDate(LocalDate birthDate, LocalDate today) {
        if (birthDate != null && (birthDate.getYear() < 1 || birthDate.isAfter(today))) throw new IllegalArgumentException("생년월일을 확인해주세요.");
    }

    @Override public String toString() { return "BasicConditions[개인정보 비공개]"; }
}
