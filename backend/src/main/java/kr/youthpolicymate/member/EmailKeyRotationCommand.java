package kr.youthpolicymate.member;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.JdbcClientAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.JdbcTemplateAutoConfiguration;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;

/** DB 연결만 시작하며 컴포넌트 검색·Flyway·웹·발송·정기 작업을 실행하지 않는다. */
public final class EmailKeyRotationCommand {
    public static void main(String[] args) {
        if (args.length != 1 || (!args[0].equals("check") && !args[0].equals("apply"))) {
            System.err.println("사용법: check | apply (서비스 중지 후 실행, 키는 환경변수로만 전달)");
            System.exit(1); return;
        }
        int exitCode = 0;
        var application = new SpringApplication(DatabaseConfiguration.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        application.setLogStartupInfo(false);
        try (var context = application.run("--spring.profiles.active=prod", "--spring.main.banner-mode=off", "--logging.level.root=OFF")) {
            var environment = context.getEnvironment();
            var current = new EmailCrypto(environment.getProperty("EMAIL_ENCRYPTION_KEY", ""));
            var rotation = new EmailKeyRotation(context.getBean(JdbcClient.class),
                    context.getBean(PlatformTransactionManager.class), current);
            boolean apply = args[0].equals("apply");
            var counts = apply ? rotation.apply(new EmailCrypto(environment.getProperty("EMAIL_ENCRYPTION_NEXT_KEY", ""))) : rotation.check();
            System.out.printf("%s | 주소 %d건 | %s 확인 코드 %d건 | %s 확인 메일 %d건%n",
                    apply ? "키 교체 완료" : "복호화 점검 완료 (DB 변경 없음)", counts.addresses(),
                    apply ? "만료한" : "만료 대상", counts.codes(), apply ? "취소한" : "취소 대상", counts.pending());
            if (apply) System.out.println("새 키를 EMAIL_ENCRYPTION_KEY에 반영한 뒤 점검하고 API를 시작해주세요.");
        } catch (Exception failure) {
            // 연결·SQL 예외에는 비밀 설정이나 데이터가 포함될 수 있어 원문을 출력하지 않는다.
            System.err.println("키 교체 작업을 완료하지 못했습니다. 키·DB 연결·잠금을 확인해주세요. 결과가 불명확하면 재적용 전에 현재 키와 새 키로 각각 check를 실행해주세요.");
            exitCode = 1;
        }
        System.exit(exitCode);
    }

    // @Configuration을 붙이지 않아 일반 API의 컴포넌트 검색에는 포함되지 않는다.
    @ImportAutoConfiguration({DataSourceAutoConfiguration.class, JdbcTemplateAutoConfiguration.class,
            JdbcClientAutoConfiguration.class, DataSourceTransactionManagerAutoConfiguration.class})
    static class DatabaseConfiguration {}
}
