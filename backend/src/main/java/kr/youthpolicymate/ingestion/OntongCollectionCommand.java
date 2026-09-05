package kr.youthpolicymate.ingestion;

import kr.youthpolicymate.YouthPolicyMateApplication;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;

import java.util.UUID;

/** 한 페이지 수집·저장 원본 재처리·최근 이력 확인을 위한 로컬 명령. */
public final class OntongCollectionCommand {
    public static void main(String[] args) {
        if (OntongSweepCommand.handles(args)) { OntongSweepCommand.main(args); return; }
        String mode;
        UUID runId;
        int page = 1;
        try {
            mode = args.length == 0 ? "status" : args[0];
            switch (mode) {
                case "fetch" -> {
                    if (args.length != 2 || !args[1].matches("[0-9]{1,4}")) throw new IllegalArgumentException();
                    page = Integer.parseInt(args[1]);
                    if (page < 1 || page > 1000) throw new IllegalArgumentException();
                    runId = UUID.randomUUID();
                }
                case "replay" -> {
                    if (args.length != 2) throw new IllegalArgumentException();
                    runId = UUID.fromString(args[1]);
                }
                case "status" -> {
                    if (args.length > 2) throw new IllegalArgumentException();
                    runId = args.length == 2 ? UUID.fromString(args[1]) : null;
                }
                default -> throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException exception) {
            System.err.println("사용법: fetch <1~1000 페이지> | replay <실행 UUID> | status [실행 UUID]");
            System.exit(1);
            return;
        }
        var application = new SpringApplication(YouthPolicyMateApplication.class);
        application.setAdditionalProfiles("local", "collection");
        application.setWebApplicationType(WebApplicationType.NONE);
        int exitCode = 0;
        try (var context = application.run("--spring.config.import=optional:file:.env[.properties]", "--app.ontong.schedule.enabled=false", "--app.reminders.enabled=false", "--app.email.enabled=false")) {
            var store = context.getBean(OntongCollectionStore.class);
            if (!mode.equals("status")) {
                System.out.println("정책 수집 실행 ID: " + runId);
                var parameters = new JobParametersBuilder().addString("runId", runId.toString())
                        .addString("mode", mode).addLong("page", (long) page)
                        .addString("invocation", UUID.randomUUID().toString()).toJobParameters();
                var execution = context.getBean(JobOperator.class).start(context.getBean("limitedOntongCollection", Job.class), parameters);
                if (execution.getStatus() != BatchStatus.COMPLETED) exitCode = 1;
            }
            System.out.println(store.requestStatus());
            var status = store.status(runId);
            if (status.isEmpty()) System.out.println("조회할 수집 이력이 없습니다.");
            status.forEach(System.out::println);
            if (runId != null) store.itemStatus(runId).forEach(System.out::println);
        } catch (Exception exception) {
            System.err.println("정책 수집 명령을 완료하지 못했습니다. DB 연결과 수집 실행 이력을 확인하세요.");
            exitCode = 1;
        }
        System.exit(exitCode);
    }
}
