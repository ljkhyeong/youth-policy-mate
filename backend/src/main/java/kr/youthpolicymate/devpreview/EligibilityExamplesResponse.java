package kr.youthpolicymate.devpreview;

import io.swagger.v3.oas.annotations.media.Schema;
import kr.youthpolicymate.policy.RecruitmentStatus;

import java.util.List;

@Schema(requiredProperties = {"dataKind", "examples"})
public record EligibilityExamplesResponse(DataKind dataKind, List<ExampleResponse> examples) {
    public enum DataKind { SYNTHETIC }

    @Schema(name = "EligibilityExample", requiredProperties = {"id", "label", "description", "result", "recruitment"})
    public record ExampleResponse(String id, String label, String description,
            EligibilityResultResponse result, RecruitmentResponse recruitment) {}

    @Schema(name = "EligibilityRecruitment", requiredProperties = {"status", "explanation"},
            description = "자격 결과와 같은 인공 정책·개정·계산 시점을 사용한 별도 모집 상태")
    public record RecruitmentResponse(RecruitmentStatus status, String explanation) {}
}
