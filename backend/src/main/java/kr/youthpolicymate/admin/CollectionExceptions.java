package kr.youthpolicymate.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import kr.youthpolicymate.policy.catalog.PolicyContent;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class CollectionExceptions {
    private CollectionExceptions() {}

    public enum Outcome { INVALID_ITEM, STORE_FAILED }

    @Schema(name = "CollectionExceptionPage", requiredProperties = {"items", "page", "pageSize", "hasNext"})
    public record Page(List<Item> items, int page, int pageSize, boolean hasNext) {}

    @Schema(name = "CollectionExceptionItem", requiredProperties = {"runId", "itemIndex", "pageNumber", "outcome",
            "attempts", "lastAttemptAt", "policyNumber"})
    public record Item(UUID runId, @Schema(description = "수집 페이지 내 0부터 시작하는 항목 위치") int itemIndex,
                       int pageNumber, Outcome outcome, int attempts,
                       @Schema(types = {"string", "null"}, format = "date-time") Instant lastAttemptAt,
                       @Schema(types = {"string", "null"}, description = "형식을 확인한 원천 정책번호. 확인 불가 시 null") String policyNumber) {}

    @Schema(name = "CollectionExceptionDetail", requiredProperties = {"item", "rawPolicyJson", "currentPolicy"})
    public record Detail(Item item,
                         @Schema(description = "저장된 수집 항목의 JSON 문자열. 외부 원문이므로 실행하지 않고 텍스트로 표시") String rawPolicyJson,
                         @Schema(types = {"object", "null"}, description = "같은 정책번호의 조회 시점 공개 내용. 없으면 null") CurrentPolicy currentPolicy) {}

    @Schema(name = "CollectionExceptionCurrentPolicy", requiredProperties = {"policyNumber", "revision", "collectedAt", "content"})
    public record CurrentPolicy(String policyNumber, long revision, Instant collectedAt, PolicyContent content) {}
}
