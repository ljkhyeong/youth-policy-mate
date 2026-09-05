package kr.youthpolicymate.policy.catalog;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import kr.youthpolicymate.eligibility.SeoulDistrict;

import java.time.LocalDate;
import java.util.Arrays;

@Schema(requiredProperties = {"birthDate", "district", "employmentStatus"})
public record BasicConditions(@NotNull LocalDate birthDate, @NotBlank @Size(max = 10) String district,
                              @NotNull EmploymentStatus employmentStatus) {
    public enum EmploymentStatus {
        EMPLOYED, SELF_EMPLOYED, NOT_EMPLOYED, FREELANCER, DAY_WORKER,
        ENTREPRENEUR, SHORT_TERM_WORKER, FARMER, OTHER
    }

    public void validate(LocalDate today) {
        if (birthDate == null || birthDate.getYear() < 1 || birthDate.isAfter(today) || employmentStatus == null
                || Arrays.stream(SeoulDistrict.values()).noneMatch(value -> value.label().equals(district))) {
            throw new IllegalArgumentException("기본 조건을 확인해주세요.");
        }
    }

    @Override public String toString() { return "BasicConditions[개인정보 비공개]"; }
}
