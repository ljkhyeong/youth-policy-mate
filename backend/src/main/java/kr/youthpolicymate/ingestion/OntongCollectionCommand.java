package kr.youthpolicymate.ingestion;

import kr.youthpolicymate.YouthPolicyMateApplication;
import kr.youthpolicymate.ingestion.OntongSweepStore.Step;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.UUID;

/** 한 페이지 수집·저장 원본 재처리·범위 수집과 최근 이력 확인을 위한 로컬 명령. */
public final class OntongCollectionCommand {
    public static void main(String[] args) {
        var mode = args.length == 0 ? "status" : args[0];
        UUID id = null;
        int first = 0, last = 0;
        try {
            switch (mode) {
                case "fetch" -> {
                    if (args.length != 2 || !args[1].matches("[0-9]{1,4}")) throw new IllegalArgumentException();
                    first = Integer.parseInt(args[1]);
                    if (first < 1 || first > 1000) throw new IllegalArgumentException();
                    id = UUID.randomUUID();
                }
                case "range" -> {
                    if (args.length != 3) throw new IllegalArgumentException();
                    first = Integer.parseInt(args[1]); last = Integer.parseInt(args[2]);
                    OntongSweepStore.validateRange(first, last);
                }
                case "replay", "range-resume", "range-abandon" -> {
                    if (args.length != 2) throw new IllegalArgumentException();
                    id = UUID.fromString(args[1]);
                }
                case "status", "range-status" -> {
                    if (args.length > 2) throw new IllegalArgumentException();
                    if (args.length == 2) id = UUID.fromString(args[1]);
                }
                default -> throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException exception) {
            System.err.println("사용법: fetch <1~1000 페이지> | replay <실행 UUID> | status [실행 UUID]"
                    + " | range <시작 페이지> <마지막 페이지> | range-status [범위 UUID] | range-resume <범위 UUID> | range-abandon <범위 UUID>");
            System.exit(1);
            return;
        }
        boolean range = mode.startsWith("range");
        int exitCode;
        try (var context = YouthPolicyMateApplication.startCommand()) {
            exitCode = range ? sweep(context, mode, id, first, last) : collect(context, mode, id, first);
        } catch (OntongApiClient.Failure failure) {
            System.err.println("범위 수집 확인 필요: " + failure.getMessage()); exitCode = 1;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt(); System.err.println("범위 수집을 중단했습니다. 실행 이력을 확인한 뒤 이어서 처리해주세요."); exitCode = 1;
        } catch (Exception exception) {
            // 외부 예외에는 인증키가 든 요청 주소가 들어갈 수 있어 원문을 출력하지 않는다.
            System.err.println(range ? "범위 수집을 완료하지 못했습니다. 설정·DB와 실행 이력을 확인해주세요."
                    : "정책 수집 명령을 완료하지 못했습니다. DB 연결과 수집 실행 이력을 확인하세요.");
            exitCode = 1;
        }
        System.exit(exitCode);
    }

    private static int collect(ConfigurableApplicationContext context, String mode, UUID runId, int page) {
        int exitCode = 0;
        var store = context.getBean(OntongCollectionStore.class);
        if (!mode.equals("status")) {
            System.out.println("정책 수집 실행 ID: " + runId);
            var service = context.getBean(OntongCollectionService.class);
            // 실패 코드는 아래 실행 이력에 함께 출력한다.
            try { if (mode.equals("fetch")) service.fetch(runId, page); else service.applyStored(runId); }
            catch (RuntimeException exception) { exitCode = 1; }
        }
        System.out.println(store.requestStatus());
        var status = store.status(runId);
        if (status.isEmpty()) System.out.println("조회할 수집 이력이 없습니다.");
        status.forEach(System.out::println);
        if (runId != null) store.itemStatus(runId).forEach(System.out::println);
        return exitCode;
    }

    private static int sweep(ConfigurableApplicationContext context, String mode, UUID id, int first, int last)
            throws InterruptedException {
        int exitCode = 0;
        var store = context.getBean(OntongSweepStore.class);
        switch (mode) {
            case "range" -> { id = store.create(first, last); System.out.println("정책 범위 실행 ID: " + id); }
            case "range-resume" -> store.resume(id);
            case "range-abandon" -> store.abandon(id);
            default -> { }
        }
        if (mode.equals("range") || mode.equals("range-resume")) {
            var runner = context.getBean(OntongSweepRunner.class);
            while (!Thread.currentThread().isInterrupted()) {
                var result = runner.tick(id);
                if (result == Step.PROGRESSED) continue;
                if (result == Step.LOCAL_REQUEST_INTERVAL) { Thread.sleep(1000); continue; }
                System.out.println("범위 수집 처리 결과: " + result);
                if (result != Step.COMPLETED) exitCode = 1;
                break;
            }
        }
        System.out.println(context.getBean(OntongCollectionStore.class).requestStatus());
        store.status(id).forEach(System.out::println);
        if (id != null) store.pageStatus(id).forEach(System.out::println);
        return exitCode;
    }
}
