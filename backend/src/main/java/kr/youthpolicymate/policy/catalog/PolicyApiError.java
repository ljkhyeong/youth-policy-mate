package kr.youthpolicymate.policy.catalog;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(requiredProperties = {"code", "message"})
public record PolicyApiError(String code, String message) {}
