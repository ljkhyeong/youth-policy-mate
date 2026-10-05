package kr.youthpolicymate.policy.catalog;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(requiredProperties = {"label", "value"})
public record PolicySourceCondition(@Schema(description = "조건 이름. 연령·소득·취업 상태·학력") String label,
                                    @Schema(description = "온통청년에 표기된 값") String value) {}
