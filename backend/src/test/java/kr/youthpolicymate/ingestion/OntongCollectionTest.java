package kr.youthpolicymate.ingestion;

import kr.youthpolicymate.policy.catalog.PolicyCatalogStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Testcontainers
@ActiveProfiles("collection")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = "ONTONG_API_KEY=collection-test-key")
class OntongCollectionTest {
    @Container @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18.6-alpine");
    @Autowired OntongCollectionStore store;
    @Autowired OntongCollectionService service;
    @Autowired PolicyCatalogStore catalog;
    @Autowired JdbcClient jdbc;
    @Autowired ObjectMapper mapper;
    @Autowired Job limitedOntongCollection;
    @Autowired JobOperator operator;
    @MockitoBean OntongApiClient client;
    private static final Instant AT = Instant.parse("2026-09-05T04:00:00Z");
    private static final String NUMBER = "20260903005400113371";

    @BeforeEach
    void prepare() {
        jdbc.sql("TRUNCATE ontong_collection_pages, ontong_collection_items, ontong_collection_item_attempts, policies, policy_source_snapshots, policy_revisions CASCADE").update();
    }

    @Test
    @DisplayName("호출 전에 실행을 저장하고 트랜잭션 밖에서 가져온 정책을 배치 이력과 함께 반영한다")
    void runsJobWithDurableHistory() throws Exception {
        var run = UUID.randomUUID();
        when(client.fetch(anyString(), eq(1))).thenAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            assertThat(store.page(run).state()).isEqualTo("FETCHING");
            return new OntongApiClient.Response(AT, body("첫 정책", "두 번째 정책"));
        });
        assertThat(job("fetch", run).getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(catalog.list("", 1, 20).total()).isEqualTo(2);
        assertThat(store.status(run).getFirst()).contains("처리 2/2", "실패 0");
        assertThat(jdbc.sql("SELECT count(*) FROM batch_job_execution WHERE status = 'COMPLETED'").query(Long.class).single()).isPositive();
        assertThat(jdbc.sql("SELECT count(*) FROM batch_job_execution_params WHERE parameter_value LIKE '%collection-test-key%'").query(Long.class).single()).isZero();
        assertThat(job("replay", run).getStatus()).isEqualTo(BatchStatus.COMPLETED);
        verify(client, times(1)).fetch(anyString(), eq(1));
        assertThat(jdbc.sql("SELECT count(*) FROM policy_revisions").query(Long.class).single()).isEqualTo(2);
        assertThat(jdbc.sql("SELECT count(*) FROM ontong_collection_item_attempts").query(Long.class).single()).isEqualTo(2);
    }

    @Test
    @DisplayName("깨진 항목의 위치를 남기고 다른 정책은 저장하며 재처리는 실패 항목만 대상으로 한다")
    void preservesPartialFailure() throws Exception {
        var run = UUID.randomUUID();
        when(client.fetch(anyString(), eq(1))).thenReturn(new OntongApiClient.Response(AT, body("정상 정책", "")));
        assertThat(job("fetch", run).getStatus()).isEqualTo(BatchStatus.FAILED);
        assertThat(catalog.list("", 1, 20).total()).isOne();
        assertThat(store.pending(run)).containsExactly(1);
        assertThat(job("replay", run).getStatus()).isEqualTo(BatchStatus.FAILED);
        assertThat(store.itemStatus(run)).anyMatch(value -> value.contains("INVALID_ITEM") && value.contains("시도 2"));
        assertThat(jdbc.sql("SELECT attempts FROM ontong_collection_items WHERE item_index = 0").query(Integer.class).single()).isOne();
        verify(client, times(1)).fetch(anyString(), eq(1));
    }

    @Test
    @DisplayName("완료 기록 저장 실패는 정책 반영도 롤백하고 저장 원본으로 재처리할 때 개정은 한 번만 만든다")
    void rollsBackAndReprocessesStoredItem() throws Exception {
        var run = UUID.randomUUID();
        when(client.fetch(anyString(), eq(1))).thenReturn(new OntongApiClient.Response(AT, body("첫 정책", "재처리할 정책", "마지막 정책")));
        jdbc.sql("""
                CREATE FUNCTION reject_item_success() RETURNS trigger LANGUAGE plpgsql AS $$
                BEGIN
                    IF NEW.item_index = 1 AND NEW.outcome = 'APPLIED' THEN RAISE EXCEPTION '의도한 완료 기록 실패'; END IF;
                    RETURN NEW;
                END $$
                """).update();
        jdbc.sql("CREATE TRIGGER reject_item_success BEFORE INSERT ON ontong_collection_item_attempts FOR EACH ROW EXECUTE FUNCTION reject_item_success()").update();
        try {
            assertThat(job("fetch", run).getStatus()).isEqualTo(BatchStatus.FAILED);
            assertThat(catalog.list("", 1, 20).total()).isEqualTo(2);
            assertThat(store.pending(run)).containsExactly(1);
        } finally {
            jdbc.sql("DROP TRIGGER reject_item_success ON ontong_collection_item_attempts").update();
            jdbc.sql("DROP FUNCTION reject_item_success()").update();
        }
        assertThat(job("replay", run).getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(catalog.list("", 1, 20).total()).isEqualTo(3);
        assertThat(jdbc.sql("SELECT count(*) FROM policy_revisions").query(Long.class).single()).isEqualTo(3);
        assertThat(jdbc.sql("SELECT outcome FROM ontong_collection_item_attempts WHERE item_index = 1 ORDER BY attempt").query(String.class).list())
                .containsExactly("STORE_FAILED", "APPLIED");
        verify(client, times(1)).fetch(anyString(), eq(1));
    }

    @Test
    @DisplayName("수집 실패와 페이지 불일치는 기존 정책을 유지하고 같은 실행을 다시 호출하지 않는다")
    void preservesCatalogOnRequestFailure() throws Exception {
        var success = stored(body("기존 정책"), AT);
        service.applyStored(success);
        var failed = UUID.randomUUID();
        when(client.fetch(anyString(), eq(1))).thenThrow(new OntongApiClient.Failure("HTTP_429"));
        assertThat(job("fetch", failed).getStatus()).isEqualTo(BatchStatus.FAILED);
        assertThat(store.status(failed).getFirst()).contains("FETCH_FAILED", "HTTP_429");
        assertThat(job("fetch", failed).getStatus()).isEqualTo(BatchStatus.FAILED);
        assertThat(job("replay", failed).getStatus()).isEqualTo(BatchStatus.FAILED);
        verify(client, times(1)).fetch(anyString(), eq(1));
        var wrongPage = stored(body("다른 페이지 정책").replace("\"pageNum\":1", "\"pageNum\":2"), AT.plusSeconds(1));
        assertThatThrownBy(() -> service.applyStored(wrongPage)).hasMessage("INVALID_LIST_RESPONSE");
        assertThat(store.page(wrongPage).rawBody()).isNotNull();
        assertThat(catalog.find(NUMBER).orElseThrow().content().title()).isEqualTo("기존 정책");
    }

    @Test
    @DisplayName("내용이 같은 후속 요청도 순번을 갱신하여 늦게 도착한 이전 응답과 과거 캡처를 차단한다")
    void rejectsLateEarlierRequest() throws Exception {
        service.applyStored(stored(body("현재 정책"), AT));
        var old = UUID.randomUUID();
        store.begin(old, 1);
        var current = stored(body("현재 정책"), AT.plusSeconds(1));
        service.applyStored(current);
        store.received(old, new OntongApiClient.Response(AT.plusSeconds(10), body("늦은 이전 내용")));
        service.applyStored(old);
        assertThat(store.itemStatus(old).getFirst()).contains("STALE");
        var parser = new OntongPolicyCapture(mapper);
        var legacy = parser.item(parser.parseResponse(body("과거 캡처"), AT.plusSeconds(20)).items().getFirst());
        assertThat(catalog.importPolicy(legacy.number(), legacy.content(), legacy.rawPolicy(), AT.plusSeconds(20), "legacy", legacy.contentHash()))
                .isEqualTo(PolicyCatalogStore.ImportResult.STALE);
        assertThat(catalog.find(NUMBER).orElseThrow().revision()).isOne();
        assertThat(catalog.find(NUMBER).orElseThrow().content().title()).isEqualTo("현재 정책");
    }

    @Test
    @DisplayName("같은 원본을 두 작업자가 재처리해도 항목 반영과 완료 이력은 한 번만 저장한다")
    void serializesConcurrentReprocessing() throws Exception {
        var run = stored(body("동시 처리 정책"), AT);
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> { start.await(); service.applyStored(run); return true; });
            var second = executor.submit(() -> { start.await(); service.applyStored(run); return true; });
            start.countDown();
            assertThat(first.get(10, TimeUnit.SECONDS)).isTrue();
            assertThat(second.get(10, TimeUnit.SECONDS)).isTrue();
        }
        assertThat(jdbc.sql("SELECT count(*) FROM policy_revisions").query(Long.class).single()).isOne();
        assertThat(jdbc.sql("SELECT count(*) FROM ontong_collection_item_attempts").query(Long.class).single()).isOne();
    }

    private JobExecution job(String mode, UUID runId) throws Exception {
        return operator.start(limitedOntongCollection, new JobParametersBuilder().addString("mode", mode)
                .addString("runId", runId.toString()).addLong("page", 1L)
                .addString("invocation", UUID.randomUUID().toString()).toJobParameters());
    }

    private UUID stored(String raw, Instant at) {
        var run = UUID.randomUUID();
        store.begin(run, 1);
        store.received(run, new OntongApiClient.Response(at, raw));
        return run;
    }

    private String body(String... titles) throws Exception {
        var capture = mapper.readTree(Files.readString(Path.of("src/test/resources/ontong/list-capture.json")));
        var body = (ObjectNode) mapper.readTree(capture.path("response").path("rawBody").asString());
        var result = (ObjectNode) body.path("result");
        ((ObjectNode) result.path("pagging")).put("pageSize", 10);
        var source = (ObjectNode) result.path("youthPolicyList").get(0);
        var items = result.putArray("youthPolicyList");
        for (int index = 0; index < titles.length; index++) {
            var item = source.deepCopy();
            item.put("plcyNm", titles[index]);
            item.put("plcyNo", NUMBER.substring(0, 18) + (71 + index));
            items.add(item);
        }
        return body.toString();
    }
}
