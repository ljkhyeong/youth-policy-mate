package kr.youthpolicymate.ingestion;

/** 운영 API의 한도를 확인한 뒤 모든 수집 프로세스에 같은 값을 설정한다. */
public record OntongRequestLimits(int dailyLimit, long intervalSeconds) {
    public OntongRequestLimits {
        if (dailyLimit < 0 || intervalSeconds < 0 || (dailyLimit == 0) != (intervalSeconds == 0))
            throw new IllegalArgumentException("일일 수집 한도와 호출 간격을 함께 설정해주세요.");
    }
    public boolean configured() { return dailyLimit > 0; }
    public void requireConfigured() {
        if (!configured()) throw new OntongApiClient.Failure("COLLECTION_LIMITS_REQUIRED");
    }
}
