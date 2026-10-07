package kr.youthpolicymate.ingestion;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

/**
 * OpenAI 규칙 추출 설정. 형식 오류는 기동 시 거절하고 누락·범위·요금 만료는 호출 직전에 보류한다.
 * 빈 환경 변수는 설정하지 않은 것으로 보므로 켜짐 여부와 횟수도 기본형 대신 null을 받아 기본값으로 바꾼다.
 */
@ConfigurationProperties("app.ai")
public record AiProperties(Boolean enabled, String apiKey, String model, BigDecimal monthlyLimitWon,
                           BigDecimal inputWonPerMillion, BigDecimal outputWonPerMillion, String pricingVersion,
                           Instant pricingValidUntil, Integer maxOutputTokens, @DefaultValue Auto auto) {
    private static final BigDecimal MONTHLY_LIMIT_CEILING = new BigDecimal("30000");

    public AiProperties {
        enabled = Boolean.TRUE.equals(enabled);
        apiKey = apiKey == null ? null : apiKey.strip();
        model = model == null ? null : model.strip();
        pricingVersion = pricingVersion == null ? null : pricingVersion.strip();
    }

    AiProperties requireUsable(Instant now) {
        if (!enabled) throw new IllegalStateException("AI 호출이 비활성화돼 있습니다. AI_ENABLED 설정을 확인해주세요.");
        require(apiKey, "OPENAI_API_KEY");
        require(model, "OPENAI_MODEL");
        require(monthlyLimitWon, "AI_MONTHLY_LIMIT_WON");
        require(inputWonPerMillion, "AI_INPUT_WON_PER_MILLION");
        require(outputWonPerMillion, "AI_OUTPUT_WON_PER_MILLION");
        require(pricingVersion, "AI_PRICING_VERSION");
        require(pricingValidUntil, "AI_PRICING_VALID_UNTIL");
        require(maxOutputTokens, "AI_MAX_OUTPUT_TOKENS");
        if (monthlyLimitWon.signum() <= 0 || monthlyLimitWon.compareTo(MONTHLY_LIMIT_CEILING) > 0
                || inputWonPerMillion.signum() <= 0 || outputWonPerMillion.signum() <= 0
                || maxOutputTokens < 1 || maxOutputTokens > 16384 || !now.isBefore(pricingValidUntil))
            throw new IllegalArgumentException("AI 한도(0원 초과, 최대 30000원)·양수 요금·출력 상한(1~16384)·요금 유효기간을 확인해주세요.");
        return this;
    }

    BigDecimal maximumWon(long inputTokens) {
        return inputWonPerMillion.multiply(BigDecimal.valueOf(inputTokens))
                .add(outputWonPerMillion.multiply(BigDecimal.valueOf(maxOutputTokens)))
                .divide(new BigDecimal("1000000"), 6, RoundingMode.CEILING);
    }

    private static void require(Object value, String name) {
        if (value == null || value instanceof String text && text.isBlank()) throw new IllegalStateException("AI 설정이 필요합니다: " + name);
    }

    // API 키가 로그·예외 메시지에 남지 않게 한다.
    @Override public String toString() {
        return "AiProperties[enabled=" + enabled + ", model=" + model + ", apiKey=****, auto=" + auto + "]";
    }

    /** enabled는 웹 서버의 정기 실행 등록과 관리자 표시에만 쓴다. auto-run 명령은 꺼진 상태에서도 한도만 확인한다. */
    public record Auto(Boolean enabled, Integer dailyLimit, Long intervalSeconds, Integer maxAttempts) {
        // 일일 한도는 0건(실행 차단), 간격은 300초, 최대 시도는 3회가 기본값이다.
        public Auto {
            enabled = Boolean.TRUE.equals(enabled);
            dailyLimit = dailyLimit == null ? 0 : dailyLimit;
            intervalSeconds = intervalSeconds == null ? 300L : intervalSeconds;
            maxAttempts = maxAttempts == null ? 3 : maxAttempts;
        }

        boolean configured() {
            return dailyLimit >= 1 && dailyLimit <= 100 && intervalSeconds >= 60 && maxAttempts >= 1 && maxAttempts <= 5;
        }
    }
}
