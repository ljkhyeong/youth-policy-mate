package kr.youthpolicymate.policy.catalog;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

@Schema(requiredProperties = {"date", "note"})
public record PolicyDeadline(@Schema(types = {"string", "null"}, format = "date") LocalDate date, String note) {
    // 저장 정책 목록의 마감일과 알림 미제공 사유. 조회 때 화면 접수 상태(검토 보정 포함)의 마감일로 만든다.
    public static PolicyDeadline from(PolicyRecruitment recruitment) {
        if (recruitment.deadlineOnSeoul() != null)
            return new PolicyDeadline(recruitment.deadlineOnSeoul(), "공고에 안내된 마감일이에요. 정확한 마감 시각은 공식 안내를 확인해주세요.");
        return new PolicyDeadline(null, switch (recruitment.status()) {
            case ROLLING -> "상시 접수라 마감 알림을 제공하지 않아요.";
            case CLOSED -> "온통청년 안내 기준으로 접수가 끝났어요.";
            // 마감일이 없는 나머지는 기간 미확인이며 설명이 미확인 이유다.
            default -> "마감일을 확인할 수 없어 알림을 예약하지 못했어요. " + recruitment.explanation();
        });
    }
}
