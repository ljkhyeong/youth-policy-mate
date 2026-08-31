package kr.youthpolicymate.ingestion;

public sealed interface CollectionPosition {
    record Page(String reference) implements CollectionPosition {
        public Page {
            requireReference(reference);
        }
    }

    record Item(String pageReference, String itemReference) implements CollectionPosition {
        public Item {
            requireReference(pageReference);
            requireReference(itemReference);
        }
    }

    private static void requireReference(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("수집 작업의 내부 참조가 필요합니다.");
        }
    }
}
