package kr.youthpolicymate.admin;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.Valid;
import kr.youthpolicymate.ingestion.OntongApiClient;
import kr.youthpolicymate.policy.catalog.PolicyApiError;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.UUID;
import java.security.Principal;

@RestController
@Profile("!preview")
@RequestMapping("/api/v1/admin/collection-exceptions")
@SecurityRequirement(name = "memberSession")
@ApiResponse(responseCode = "400", description = "조회 조건 오류", content = @Content(schema = @Schema(implementation = PolicyApiError.class)))
@ApiResponse(responseCode = "401", description = "로그인 필요", content = @Content(schema = @Schema(implementation = PolicyApiError.class)))
@ApiResponse(responseCode = "403", description = "관리자 권한 없음", content = @Content(schema = @Schema(implementation = PolicyApiError.class)))
@ApiResponse(responseCode = "503", description = "수집 처리 또는 이력 조회 실패", content = @Content(schema = @Schema(implementation = PolicyApiError.class)))
public class CollectionExceptionController {
    private final CollectionExceptionStore store;
    private final CollectionReplayService replays;

    CollectionExceptionController(CollectionExceptionStore store, CollectionReplayService replays) {
        this.store = store;
        this.replays = replays;
    }

    @PostMapping("/{runId}/{itemIndex}/replays")
    @Operation(operationId = "replayCollectionItem", summary = "관리자 사유를 남기고 저장 원본의 실패 항목 하나를 재처리",
            description = "같은 요청 ID·항목·작업자·사유·처리 횟수는 기존 결과를 반환한다. 원본 수정이나 외부 수집을 실행하지 않는다.")
    @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = CollectionReplays.Result.class)))
    @ApiResponse(responseCode = "409", description = "처리 상태 변경 또는 저장 원본 재처리 불가", content = @Content(schema = @Schema(implementation = PolicyApiError.class)))
    public ResponseEntity<CollectionReplays.Result> replay(@PathVariable UUID runId,
            @PathVariable @Min(0) @Max(9) int itemIndex, @Valid @RequestBody CollectionReplays.Request request, Principal principal) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(replays.replay(runId, itemIndex, UUID.fromString(principal.getName()), request));
    }

    @GetMapping("/replays")
    @Operation(operationId = "listCollectionReplays", summary = "관리자 수집 항목 재처리 이력")
    @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = CollectionReplays.Page.class)))
    public ResponseEntity<CollectionReplays.Page> replays(@RequestParam(defaultValue = "1") @Min(1) @Max(1000) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int pageSize) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(replays.list(page, pageSize));
    }

    @GetMapping
    @Operation(operationId = "listCollectionExceptions", summary = "관리자 전용 수집 항목 검증·저장 실패 목록",
            description = "최근 처리 시각 순. 재처리에 성공한 항목은 제외하며, 같은 시각에는 수집 요청 순번 역순·항목 위치 순으로 정렬한다.")
    @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = CollectionExceptions.Page.class)))
    public ResponseEntity<CollectionExceptions.Page> list(
            @RequestParam(defaultValue = "1") @Min(1) @Max(1000) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int pageSize) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(store.list(page, pageSize));
    }

    @GetMapping("/pages")
    @Operation(operationId = "listCollectionPageFailures", summary = "관리자 전용 페이지 수집 실패 목록",
            description = "수집 요청 순번 역순. 정상 처리된 페이지는 제외한다. 원본 응답과 임의 오류 문자열을 노출하지 않으며 재처리를 실행하지 않는다.")
    @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = CollectionExceptions.PageFailureList.class)))
    public ResponseEntity<CollectionExceptions.PageFailureList> pageFailures(
            @RequestParam(defaultValue = "1") @Min(1) @Max(1000) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int pageSize) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(store.pageFailures(page, pageSize));
    }

    @GetMapping("/{runId}/{itemIndex}")
    @Operation(operationId = "getCollectionException", summary = "관리자 전용 수집 실패 원본·현재 정책 조회",
            description = "저장된 실패 유형만 제공하며 구체적인 실패 원인을 추정하지 않는다. 원본 수정이나 재처리를 실행하지 않는다.")
    @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = CollectionExceptions.Detail.class)))
    @ApiResponse(responseCode = "404", description = "현재 실패 상태인 항목 없음", content = @Content(schema = @Schema(implementation = PolicyApiError.class)))
    public ResponseEntity<?> detail(@PathVariable UUID runId, @PathVariable @Min(0) @Max(9) int itemIndex) {
        return store.detail(runId, itemIndex).<ResponseEntity<?>>map(value -> ResponseEntity.ok()
                .cacheControl(CacheControl.noStore()).body(value))
                .orElseGet(() -> error(404, "COLLECTION_EXCEPTION_NOT_FOUND", "수집 실패 항목을 찾을 수 없습니다."));
    }

    @ExceptionHandler({HandlerMethodValidationException.class, MethodArgumentTypeMismatchException.class})
    ResponseEntity<PolicyApiError> invalid() {
        return error(400, "INVALID_COLLECTION_QUERY", "수집 실행 ID·항목 위치·페이지를 확인해주세요.");
    }

    @ExceptionHandler(DataAccessException.class)
    ResponseEntity<PolicyApiError> unavailable() {
        return error(503, "COLLECTION_UNAVAILABLE", "수집 작업을 완료하지 못했습니다. 다시 확인해주세요.");
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
    ResponseEntity<PolicyApiError> invalidReplay() {
        return error(400, "INVALID_COLLECTION_REPLAY", "재처리 사유와 요청 정보를 확인해주세요.");
    }

    @ExceptionHandler({CollectionReplays.Changed.class, DuplicateKeyException.class})
    ResponseEntity<PolicyApiError> changed() {
        return error(409, "COLLECTION_REPLAY_CHANGED", "처리 상태나 요청 정보가 바뀌었습니다. 최신 항목을 확인해주세요.");
    }

    @ExceptionHandler(OntongApiClient.Failure.class)
    ResponseEntity<PolicyApiError> replayUnavailable() {
        return error(409, "COLLECTION_REPLAY_UNAVAILABLE", "저장된 원본으로 재처리할 수 없습니다. 원본과 실행 상태를 확인해주세요.");
    }

    private static ResponseEntity<PolicyApiError> error(int status, String code, String message) {
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(new PolicyApiError(code, message));
    }
}
