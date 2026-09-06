package kr.youthpolicymate.ingestion;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
        "ONTONG_API_KEY=sweep-test-key", "ONTONG_COLLECTION_DAILY_LIMIT=3", "ONTONG_COLLECTION_INTERVAL_SECONDS=30"})
class OntongSweepTest {
    @Container @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18.6-alpine");
    @Autowired OntongSweepStore sweeps;
    @Autowired OntongSweepRunner runner;
    @MockitoSpyBean OntongCollectionStore pages;
    @MockitoSpyBean JdbcClient jdbc;
    @Autowired ObjectMapper mapper;
    @Autowired ApplicationContext context;
    @MockitoBean OntongApiClient client;
    @MockitoBean Clock clock;
    private final AtomicReference<Instant> now = new AtomicReference<>();

    @BeforeEach
    void prepare() {
        jdbc.sql("TRUNCATE ontong_collection_sweeps, ontong_collection_sweep_pages, ontong_collection_pages, policies CASCADE").update();
        jdbc.sql("UPDATE ontong_collection_request_gate SET next_request_at = '-infinity' WHERE id = 1").update();
        now.set(Instant.parse("2026-09-05T14:57:30Z"));
        when(clock.instant()).thenAnswer(call -> now.get()); when(clock.getZone()).thenReturn(ZoneOffset.UTC);
    }

    @Test @DisplayName("지정 범위를 호출 간격에 맞춰 배치로 처리하고 정기 실행은 기본적으로 꺼져 있다")
    void collectsBoundedPagesWithBatchHistory() throws Exception {
        assertThat(context.getBeansOfType(OntongSweepScheduler.class)).isEmpty();
        when(client.fetch(anyString(), anyInt())).thenAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            int page = call.getArgument(1); return response(page, page == 1 ? 10 : 2, 12, false);
        });
        var id = sweeps.create(1, 2);
        assertThat(runner.tick(id)).isEqualTo("PROGRESSED");
        assertThat(runner.tick(id)).isEqualTo("LOCAL_REQUEST_INTERVAL");
        advance(30); assertThat(runner.tick(id)).isEqualTo("PROGRESSED");
        assertThat(runner.tick(id)).isEqualTo("COMPLETED");
        assertThat(sweeps.status(id).getFirst()).contains("완료 페이지 2", "확인 필요 0");
        assertThat(jdbc.sql("SELECT count(*) FROM policies").query(Long.class).single()).isEqualTo(12);
        assertThat(jdbc.sql("SELECT count(*) FROM batch_job_execution WHERE status = 'COMPLETED'").query(Long.class).single()).isPositive();
        verify(client, times(2)).fetch(anyString(), anyInt());
    }

    @Test @DisplayName("동시에 같은 위치를 배정해도 한 요청만 만들고 저장된 응답은 재시작 뒤 외부 호출 없이 처리한다")
    void claimsOnceAndResumesStoredResponse() throws Exception {
        var id = sweeps.create(1, 1); var start = new CountDownLatch(1);
        List<OntongSweepStore.Work> works;
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> { start.await(); return sweeps.claim(id); });
            var second = executor.submit(() -> { start.await(); return sweeps.claim(id); });
            start.countDown(); works = List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
        }
        assertThat(works).extracting(OntongSweepStore.Work::kind).containsExactlyInAnyOrder("FETCH", "WAITING_RESPONSE");
        var request = works.stream().filter(work -> work.kind().equals("FETCH")).findFirst().orElseThrow();
        assertThat(runner.tick(id)).isEqualTo("WAITING_RESPONSE");
        advance(25); pages.startDispatch(request.runId());
        assertThatThrownBy(() -> pages.startDispatch(request.runId())).hasMessage("REQUEST_ALREADY_ATTEMPTED");
        advance(5);
        assertThatThrownBy(() -> pages.begin(UUID.randomUUID(), 2)).hasMessage("LOCAL_REQUEST_INTERVAL");
        pages.received(request.runId(), response(1, 2, 2, false));
        clearInvocations(pages);
        assertThat(runner.tick(id)).isEqualTo("PROGRESSED");
        verify(pages, times(1)).page(request.runId());
        assertThat(sweeps.find(id).state()).isEqualTo("COMPLETED");
        verifyNoInteractions(client);
    }

    @Test @DisplayName("이후 요청이 예약된 뒤 늦게 발송하려는 이전 요청은 원천 호출 전에 거절한다")
    void rejectsDispatchAfterReservationWasReplaced() {
        var earlier = UUID.randomUUID(); pages.begin(earlier, 1); advance(30);
        var later = UUID.randomUUID(); pages.begin(later, 2);
        assertThatThrownBy(() -> pages.startDispatch(earlier)).hasMessage("REQUEST_RESERVATION_CHANGED");
        clearInvocations(jdbc);
        pages.startDispatch(later);
        verify(jdbc, times(1)).sql(startsWith("SELECT"));
        verifyNoInteractions(client);
    }

    @Test @DisplayName("페이지 배정 저장이 실패하면 요청 기록과 호출 간격 예약도 함께 롤백한다")
    void rollsBackRequestAdmissionWithAssignment() {
        var id = sweeps.create(1, 1);
        jdbc.sql("CREATE FUNCTION reject_sweep_assignment() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION '의도한 배정 실패'; END $$").update();
        jdbc.sql("CREATE TRIGGER reject_sweep_assignment BEFORE INSERT ON ontong_collection_sweep_pages FOR EACH ROW EXECUTE FUNCTION reject_sweep_assignment()").update();
        try {
            assertThatThrownBy(() -> sweeps.claim(id)).isInstanceOf(RuntimeException.class);
            assertThat(jdbc.sql("SELECT count(*) FROM ontong_collection_pages").query(Long.class).single()).isZero();
        } finally {
            jdbc.sql("DROP TRIGGER reject_sweep_assignment ON ontong_collection_sweep_pages").update();
            jdbc.sql("DROP FUNCTION reject_sweep_assignment()").update();
        }
        assertThat(sweeps.claim(id).kind()).isEqualTo("FETCH");
        verifyNoInteractions(client);
    }

    @Test @DisplayName("항목 실패는 다음 페이지를 막지 않으며 재개는 실패한 원본만 다시 처리한다")
    void continuesAfterItemFailureAndReplaysWithoutRefetch() throws Exception {
        when(client.fetch(anyString(), eq(1))).thenReturn(response(1, 10, 12, true));
        when(client.fetch(anyString(), eq(2))).thenReturn(response(2, 2, 12, false));
        var id = sweeps.create(1, 2);
        assertThat(runner.tick(id)).isEqualTo("PROGRESSED"); advance(30);
        assertThat(runner.tick(id)).isEqualTo("PROGRESSED");
        assertThat(sweeps.find(id).state()).isEqualTo("PARTIAL");
        assertThat(jdbc.sql("SELECT count(*) FROM policies").query(Long.class).single()).isEqualTo(11);
        sweeps.resume(id); runner.tick(id); runner.tick(id);
        assertThat(sweeps.find(id).state()).isEqualTo("PARTIAL");
        assertThat(jdbc.sql("SELECT max(attempts) FROM ontong_collection_items WHERE outcome = 'INVALID_ITEM'").query(Integer.class).single()).isEqualTo(2);
        assertThat(jdbc.sql("SELECT max(attempts) FROM ontong_collection_items WHERE outcome = 'APPLIED'").query(Integer.class).single()).isOne();
        verify(client, times(2)).fetch(anyString(), anyInt());
    }

    @Test @DisplayName("429 응답은 범위 실행을 멈추며 반복 조회와 재개로 원천 요청을 재전송하지 않는다")
    void pausesRateLimitedRequestWithoutAutomaticRetry() {
        when(client.fetch(anyString(), anyInt())).thenThrow(new OntongApiClient.Failure("HTTP_429"));
        var id = sweeps.create(1, 2);
        assertThat(runner.tick(id)).isEqualTo("PAUSED"); advance(60);
        assertThat(runner.tick(id)).isEqualTo("PAUSED");
        assertThatThrownBy(() -> sweeps.resume(id)).hasMessage("RESPONSE_UNKNOWN_REQUIRES_REVIEW");
        assertThat(sweeps.status(id).getFirst()).contains("HTTP_429");
        sweeps.abandon(id); assertThat(sweeps.find(id).state()).isEqualTo("ABANDONED");
        verify(client, times(1)).fetch(anyString(), anyInt());
    }

    @Test @DisplayName("일일 요청 한도를 넘기지 않고 서울 날짜가 바뀐 뒤 같은 위치에서 이어 간다")
    void defersAtDailyLimitAndContinuesAfterSeoulMidnight() throws Exception {
        when(client.fetch(anyString(), anyInt())).thenAnswer(call -> response(call.getArgument(1), 10, 40, false));
        var id = sweeps.create(1, 4);
        for (int i = 0; i < 3; i++) { assertThat(runner.tick(id)).isEqualTo("PROGRESSED"); advance(30); }
        assertThat(runner.tick(id)).isEqualTo("LOCAL_DAILY_LIMIT");
        assertThat(sweeps.find(id).nextPage()).isEqualTo(4);
        assertThatThrownBy(() -> pages.begin(UUID.randomUUID(), 5)).hasMessage("LOCAL_DAILY_LIMIT");
        now.set(Instant.parse("2026-09-05T15:00:00Z"));
        assertThat(runner.tick(id)).isEqualTo("PROGRESSED");
        assertThat(sweeps.find(id).state()).isEqualTo("COMPLETED");
        verify(client, times(4)).fetch(anyString(), anyInt());
    }

    @Test @DisplayName("예상보다 빈 페이지를 전체 수집 완료로 보지 않고 다음 지정 페이지도 처리한다")
    void marksUnexpectedPageSizeForReview() throws Exception {
        when(client.fetch(anyString(), eq(1))).thenReturn(response(1, 0, 20, false));
        when(client.fetch(anyString(), eq(2))).thenReturn(response(2, 10, 20, false));
        var id = sweeps.create(1, 2); runner.tick(id); advance(30); runner.tick(id);
        assertThat(sweeps.find(id).state()).isEqualTo("PARTIAL");
        assertThat(sweeps.pageStatus(id).getFirst()).contains("UNEXPECTED_ITEM_COUNT");
        verify(client, times(1)).fetch(anyString(), eq(2));
    }

    @Test @DisplayName("정기 실행은 기존 범위를 재사용하고 완료 후 설정한 간격에만 다음 범위를 만든다")
    void schedulesOnlyOneRangeAfterConfiguredInterval() throws Exception {
        var interval = Duration.ofHours(24);
        var id = sweeps.scheduled(1, 1, interval).orElseThrow();
        assertThat(sweeps.scheduled(1, 1, interval)).contains(id);
        assertThatThrownBy(() -> sweeps.create(1, 2)).hasMessage("SWEEP_ALREADY_OPEN");
        when(client.fetch(anyString(), eq(1))).thenReturn(response(1, 1, 1, false)); runner.tick(id);
        assertThat(sweeps.scheduled(1, 1, interval)).isEmpty(); advance(24 * 3600 - 1);
        assertThat(sweeps.scheduled(1, 1, interval)).isEmpty(); advance(1);
        assertThat(sweeps.scheduled(1, 1, interval).orElseThrow()).isNotEqualTo(id);
        verify(client, times(1)).fetch(anyString(), eq(1));
    }

    private void advance(long seconds) { now.updateAndGet(value -> value.plusSeconds(seconds)); }
    private OntongApiClient.Response response(int page, int count, int total, boolean invalidFirst) throws Exception {
        var capture = mapper.readTree(Files.readString(Path.of("src/test/resources/ontong/list-capture.json")));
        var body = (ObjectNode) mapper.readTree(capture.path("response").path("rawBody").asString());
        var result = (ObjectNode) body.path("result");
        ((ObjectNode) result.path("pagging")).put("pageNum", page).put("pageSize", 10).put("totCount", total);
        var source = (ObjectNode) result.path("youthPolicyList").get(0); var items = result.putArray("youthPolicyList");
        for (int i = 0; i < count; i++) {
            var item = source.deepCopy(); item.put("plcyNo", Integer.toString(100000 + page * 10 + i));
            item.put("plcyNm", invalidFirst && i == 0 ? "" : "검증 정책 " + page + "-" + i); items.add(item);
        }
        return new OntongApiClient.Response(now.get(), body.toString());
    }
}
