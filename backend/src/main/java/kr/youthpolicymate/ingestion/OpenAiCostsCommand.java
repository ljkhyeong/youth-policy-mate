package kr.youthpolicymate.ingestion;

import org.springframework.web.client.RestClient;
import java.io.PrintStream;
import java.time.Clock;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.Map;

public final class OpenAiCostsCommand {
    public static void main(String[] args) {
        System.exit(run(args, System.getenv(), RestClient.builder().baseUrl("https://api.openai.com/v1"),
                Clock.systemUTC(), System.out, System.err));
    }

    static int run(String[] args, Map<String, String> environment, RestClient.Builder builder,
                   Clock clock, PrintStream output, PrintStream error) {
        YearMonth month;
        try {
            if (args.length != 1) throw new IllegalArgumentException();
            month = YearMonth.parse(args[0]);
            if (month.getYear() < 1 || month.getYear() > 9999) throw new IllegalArgumentException();
        } catch (IllegalArgumentException | DateTimeParseException invalid) {
            error.println("사용법: <YYYY-MM> — OpenAI 프로젝트의 UTC 월 비용 조회"); return 1;
        }
        try {
            var report = new OpenAiCostsClient(environment.get("OPENAI_ADMIN_KEY"), environment.get("OPENAI_COSTS_PROJECT_ID"), builder)
                    .month(month, clock.instant());
            output.printf("OpenAI 프로젝트 %s 비용 | UTC %s 이상 ~ %s 미만 | 조회 %s%n", report.project(), report.start(), report.end(), report.checkedAt());
            if (report.totals().isEmpty()) output.println("공급자가 반환한 비용 항목이 없습니다.");
            report.totals().entrySet().stream().sorted(Map.Entry.comparingByKey())
                    .forEach(total -> output.println(total.getKey() + " " + total.getValue().toPlainString()));
            output.println("조회 시점의 집계액입니다. 최종 청구·개별 요청의 무과금을 확정하거나 원화 환산·예산 정산에 자동 적용하지 않습니다.");
            return 0;
        } catch (OpenAiCostsClient.Unavailable failure) { error.println(failure.getMessage()); return 1; }
        catch (RuntimeException failure) { error.println("OpenAI 비용 조회를 완료하지 못했습니다. 설정을 확인해주세요."); return 1; }
    }
}
