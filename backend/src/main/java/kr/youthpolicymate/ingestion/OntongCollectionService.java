package kr.youthpolicymate.ingestion;

import org.springframework.stereotype.Service;

import java.util.UUID;

/** 외부 호출과 항목 순회는 트랜잭션 밖에서 하고, 원본 저장·항목 반영만 저장소에서 짧게 커밋한다. */
@Service
public class OntongCollectionService {
    private final OntongCollectionStore store;
    private final OntongApiClient client;
    private final OntongPolicyCapture parser;
    private final OntongProperties properties;

    OntongCollectionService(OntongCollectionStore store, OntongApiClient client, OntongPolicyCapture parser,
                            OntongProperties properties) {
        this.store = store;
        this.client = client;
        this.parser = parser;
        this.properties = properties;
    }

    /** 요청 순번을 커밋한 뒤 한 번 호출하고 받은 원본을 반영한다. 실패 코드는 예외 메시지와 실행 이력에 남는다. */
    public void fetch(UUID runId, int page) {
        store.begin(runId, page);
        receive(runId);
        applyStored(runId);
    }

    // 범위 실행이 사전에 저장한 요청만 보낸다. 중단 후에는 저장 원본만 재처리한다.
    void receive(UUID runId) {
        var page = store.pageStatus(runId);
        store.startDispatch(runId);
        try { store.received(runId, client.fetch(properties.apiKey(), page.number())); }
        catch (OntongApiClient.Failure failure) {
            store.failed(runId, failure.getMessage(), false);
            throw failure;
        } catch (RuntimeException exception) {
            store.failed(runId, "RESPONSE_STORE_FAILED", false);
            throw new OntongApiClient.Failure("RESPONSE_STORE_FAILED");
        }
    }

    public void applyStored(UUID runId) {
        var page = store.page(runId);
        var parsed = readStored(page);
        store.prepare(runId, parsed);
        for (int index : store.pending(runId)) {
            try { store.apply(page, parsed, index); }
            catch (RuntimeException exception) { store.itemFailed(runId, index); }
        }
        if (!store.pending(runId).isEmpty()) throw new OntongApiClient.Failure("ITEMS_REQUIRE_REPROCESSING");
    }

    public void applyStoredItem(UUID runId, int index) {
        var page = store.page(runId);
        var parsed = readStored(page);
        if (index >= parsed.items().size()) throw new OntongApiClient.Failure("ITEM_NOT_STORED");
        store.apply(page, parsed, index);
    }

    private OntongPolicyCapture.Parsed readStored(OntongCollectionStore.Page page) {
        if (page.rawBody() == null) throw new OntongApiClient.Failure("RESPONSE_NOT_STORED");
        OntongPolicyCapture.Parsed parsed;
        try {
            parsed = parser.parseResponse(page.rawBody(), page.receivedAt());
            if (parsed.page() != page.number() || parsed.pageSize() != 10) throw new IllegalArgumentException();
        } catch (IllegalArgumentException exception) {
            store.failed(page.runId(), "INVALID_LIST_RESPONSE", true);
            throw new OntongApiClient.Failure("INVALID_LIST_RESPONSE");
        }
        return parsed;
    }
}
