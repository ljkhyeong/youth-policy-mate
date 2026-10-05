package kr.youthpolicymate.policy.catalog;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

@Schema(requiredProperties = {"policyNumber", "revision", "content", "sourceUrl", "collectedAt", "recruitment", "sourceNotices", "sourceConditions"})
public record PolicyDetailResponse(String policyNumber,
                                   @Schema(description = "서비스 내부 적용 개정 번호") long revision,
                                   PolicyContent content, String sourceUrl, Instant collectedAt, PolicyRecruitment recruitment,
                                   @Schema(description = "현재 수집 내용에서 확인한 조건 충돌 안내. 빈 배열은 검토 완료를 뜻하지 않는다.")
                                   List<PolicySourceNotice> sourceNotices,
                                   @Schema(description = "온통청년에 등록된 조건 표기. 자격 판정이 아니며 제한을 표기한 값만 담는다. 빈 배열은 조건이 없다는 뜻이 아니다.")
                                   List<PolicySourceCondition> sourceConditions) {}
