package kr.youthpolicymate.ingestion;

import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

public class OntongCollectionService {
    private final OntongCollectionStore store;
    private final OntongApiClient client;
    private final OntongPolicyCapture parser;

    public OntongCollectionService(OntongCollectionStore store, OntongApiClient client, ObjectMapper mapper) {
        this.store = store;
        this.client = client;
        this.parser = new OntongPolicyCapture(mapper);
    }

    public void fetch(UUID runId, int page, String apiKey) {
        store.begin(runId, page);
        receive(runId, apiKey);
    }

    // 범위 실행이 사전에 저장한 요청만 보낸다. 중단 후에는 저장 원본만 재처리한다.
    void receive(UUID runId, String apiKey) {
        var page = store.pageStatus(runId);
        store.startDispatch(runId);
        try { store.received(runId, client.fetch(apiKey, page.number())); }
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
        if (page.rawBody() == null) throw new OntongApiClient.Failure("RESPONSE_NOT_STORED");
        OntongPolicyCapture.Parsed parsed;
        try {
            parsed = parser.parseResponse(page.rawBody(), page.receivedAt());
            if (parsed.page() != page.number() || parsed.pageSize() != 10) throw new IllegalArgumentException();
        } catch (IllegalArgumentException exception) {
            store.failed(runId, "INVALID_LIST_RESPONSE", true);
            throw new OntongApiClient.Failure("INVALID_LIST_RESPONSE");
        }
        store.prepare(runId, parsed);
        for (int index : store.pending(runId)) {
            try { store.apply(page, parsed, index); }
            catch (RuntimeException exception) { store.itemFailed(runId, index); }
        }
        if (!store.pending(runId).isEmpty()) throw new OntongApiClient.Failure("ITEMS_REQUIRE_REPROCESSING");
    }
}
