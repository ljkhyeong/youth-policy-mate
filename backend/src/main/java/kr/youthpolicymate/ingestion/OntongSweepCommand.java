package kr.youthpolicymate.ingestion;

import kr.youthpolicymate.YouthPolicyMateApplication;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import java.util.List;
import java.util.UUID;

/** 범위 실행과 원본 재처리를 운영자가 명시적으로 시작하는 명령이다. */
public final class OntongSweepCommand {
    static boolean handles(String[] args) {
        return args.length > 0 && List.of("range", "range-status", "range-resume", "range-abandon").contains(args[0]);
    }
    public static void main(String[] args) {
        int first = 0, last = 0; UUID id = null;
        try {
            if (args[0].equals("range")) {
                if (args.length != 3) throw new IllegalArgumentException();
                first = Integer.parseInt(args[1]); last = Integer.parseInt(args[2]); OntongSweepStore.validateRange(first, last);
            } else if (args[0].equals("range-status")) {
                if (args.length > 2) throw new IllegalArgumentException();
                if (args.length == 2) id = UUID.fromString(args[1]);
            } else {
                if (args.length != 2) throw new IllegalArgumentException();
                id = UUID.fromString(args[1]);
            }
        } catch (IllegalArgumentException exception) {
            System.err.println("사용법: range <시작 페이지> <마지막 페이지> | range-status [범위 UUID] | range-resume <범위 UUID> | range-abandon <범위 UUID>");
            System.exit(1); return;
        }
        var application = new SpringApplication(YouthPolicyMateApplication.class);
        application.setAdditionalProfiles("local", "collection");
        application.setWebApplicationType(WebApplicationType.NONE);
        int exitCode = 0;
        try (var context = application.run("--spring.config.import=optional:file:.env[.properties]",
                "--app.ontong.schedule.enabled=false", "--app.reminders.enabled=false", "--app.email.enabled=false")) {
            var store = context.getBean(OntongSweepStore.class);
            if (args[0].equals("range")) { id = store.create(first, last); System.out.println("정책 범위 실행 ID: " + id); }
            if (args[0].equals("range-resume")) store.resume(id);
            if (args[0].equals("range-abandon")) store.abandon(id);
            if (args[0].equals("range") || args[0].equals("range-resume")) {
                var runner = context.getBean(OntongSweepRunner.class);
                while (!Thread.currentThread().isInterrupted()) {
                    var result = runner.tick(id);
                    if (result.equals("PROGRESSED")) continue;
                    if (result.equals("LOCAL_REQUEST_INTERVAL")) { Thread.sleep(1000); continue; }
                    System.out.println("범위 수집 처리 결과: " + result);
                    if (!result.equals("COMPLETED")) exitCode = 1;
                    break;
                }
            }
            System.out.println(context.getBean(OntongCollectionStore.class).requestStatus());
            store.status(id).forEach(System.out::println);
            if (id != null) store.pageStatus(id).forEach(System.out::println);
        } catch (OntongApiClient.Failure failure) {
            System.err.println("범위 수집 확인 필요: " + failure.getMessage()); exitCode = 1;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt(); System.err.println("범위 수집을 중단했습니다. 실행 이력을 확인한 뒤 이어서 처리해주세요."); exitCode = 1;
        } catch (Exception exception) {
            System.err.println("범위 수집을 완료하지 못했습니다. 설정·DB와 실행 이력을 확인해주세요."); exitCode = 1;
        }
        System.exit(exitCode);
    }
}
