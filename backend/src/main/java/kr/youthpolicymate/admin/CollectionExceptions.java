package kr.youthpolicymate.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import kr.youthpolicymate.policy.catalog.PolicyContent;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class CollectionExceptions {
    private CollectionExceptions() {}

    public enum Outcome { INVALID_ITEM, STORE_FAILED }
    public enum PageState { FETCH_FAILED, INVALID_RESPONSE }
    public enum PageFailureReason {
        API_KEY_MISSING, HTTP_ERROR, NON_JSON_RESPONSE, SECRET_IN_RESPONSE, REQUEST_INTERRUPTED,
        REQUEST_OR_RESPONSE_FAILED, RESPONSE_STORE_FAILED, INVALID_LIST_RESPONSE, UNKNOWN
    }

    @Schema(name = "CollectionPageFailureList", requiredProperties = {"items", "page", "pageSize", "hasNext"})
    public record PageFailureList(List<PageFailure> items, int page, int pageSize, boolean hasNext) {}

    @Schema(name = "CollectionPageFailure", requiredProperties = {"runId", "pageNumber", "state", "reason", "httpStatus",
            "startedAt", "dispatchedAt", "receivedAt", "responseStored"})
    public record PageFailure(UUID runId, int pageNumber, PageState state, PageFailureReason reason,
                              @Schema(types = {"integer", "null"}, format = "int32") Integer httpStatus,
                              Instant startedAt,
                              @Schema(types = {"string", "null"}, format = "date-time") Instant dispatchedAt,
                              @Schema(types = {"string", "null"}, format = "date-time") Instant receivedAt,
                              boolean responseStored) {}

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

    @Schema(name = "CollectionExceptionCurrentPolicy", requiredProperties = {"policyNumber", "revision", "collectedAt", "content", "sourceCapturedAt", "previousRevision"})
    public record CurrentPolicy(String policyNumber, long revision, Instant collectedAt, PolicyContent content,
                                @Schema(description = "현재 개정이 참조하는 원본 수집 시각. 개정 적용 시각이 아님") Instant sourceCapturedAt,
                                @Schema(types = {"object", "null"}, description = "같은 정책의 직전 내부 개정. 없으면 null") Revision previousRevision) {}

    @Schema(name = "CollectionExceptionRevision", requiredProperties = {"revision", "sourceCapturedAt", "content"})
    public record Revision(@Schema(description = "서비스 내부 개정 번호") long revision,
                           @Schema(description = "개정이 참조하는 원본 수집 시각. 개정 적용 시각이 아님") Instant sourceCapturedAt,
                           PolicyContent content) {}
}
