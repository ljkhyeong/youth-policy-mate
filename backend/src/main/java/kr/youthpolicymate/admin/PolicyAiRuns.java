package kr.youthpolicymate.admin;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class PolicyAiRuns {
    private PolicyAiRuns() {}
    public enum State { RUNNING, COMPLETED, RETRY_PENDING, INTERRUPTED, REVIEW_REQUIRED, SUPERSEDED, LEASE_EXPIRED }
    public enum Filter { ALL, RUNNING, COMPLETED, RETRY_PENDING, INTERRUPTED, REVIEW_REQUIRED, SUPERSEDED, LEASE_EXPIRED }

    @Schema(name = "PolicyAiRunPage", requiredProperties = {"items", "page", "pageSize", "total", "hasNext", "checkedAt", "automationEnabled"})
    public record Page(List<Item> items, int page, int pageSize, long total, boolean hasNext, Instant checkedAt,
                       @Schema(description = "자동 실행 스위치. AI 설정·예산·일일 한도 충족 여부를 뜻하지 않는다") boolean automationEnabled) {}

    @Schema(name = "PolicyAiRunItem", requiredProperties = {"requestId", "policyNumber", "title", "revision", "currentRevision",
            "sourceMatches", "latestRequest", "attempt", "state", "startedAt", "finishedAt", "resultCode", "candidateStatus", "reservationPhase", "responseStored"})
    public record Item(UUID requestId, String policyNumber, String title, long revision, long currentRevision,
                       boolean sourceMatches, boolean latestRequest, int attempt,
                       @Schema(description = "요청별 마지막 자동 시도 상태. 실행 기한이 지난 RUNNING은 LEASE_EXPIRED로 표시하며 DB는 변경하지 않는다") State state,
                       Instant startedAt,
                       @Schema(types = {"string", "null"}, format = "date-time") Instant finishedAt,
                       @Schema(types = {"string", "null"}, description = "마지막 시도 종료 시 기록한 처리 코드") String resultCode,
                       @Schema(types = {"string", "null"}, description = "조회 시점의 추출 결과. 마지막 시도 종료 후 저장됐을 수 있다") String candidateStatus,
                       @Schema(types = {"string", "null"}, description = "조회 시점의 비용 예약 상태") String reservationPhase,
                       boolean responseStored) {}
}
