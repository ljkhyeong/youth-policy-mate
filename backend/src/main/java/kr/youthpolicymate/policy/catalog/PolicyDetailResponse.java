package kr.youthpolicymate.policy.catalog;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

@Schema(requiredProperties = {"policyNumber", "revision", "content", "sourceUrl", "collectedAt", "recruitment", "sourceNotices"})
public record PolicyDetailResponse(String policyNumber,
                                   @Schema(description = "서비스 내부 적용 개정 번호") long revision,
                                   PolicyContent content, String sourceUrl, Instant collectedAt, PolicyRecruitment recruitment,
                                   @Schema(description = "현재 수집 내용에서 확인한 조건 충돌 안내. 빈 배열은 검토 완료를 뜻하지 않는다.")
                                   List<PolicySourceNotice> sourceNotices) {}
