package kr.youthpolicymate.ingestion;

import kr.youthpolicymate.YouthPolicyMateApplication;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.UUID;

public final class PolicyAiRuleCommand {
    public static void main(String[] args) throws Exception {
        if (!(args.length == 6 && args[0].equals("prepare") || args.length == 3 && args[0].equals("complete")
                || args.length == 2 && args[0].equals("status")))
            throw new IllegalArgumentException("사용법: prepare <정책번호> <개정> <생성 방식> <요청 UUID> <새 JSON 경로> | complete <요청 UUID> <결과 JSON 경로> | status <요청 UUID>");
        var app = new SpringApplication(YouthPolicyMateApplication.class);
        app.setAdditionalProfiles("local");
        app.setWebApplicationType(WebApplicationType.NONE);
        try (var context = app.run("--spring.config.import=optional:file:.env[.properties]", "--app.ontong.schedule.enabled=false",
                "--app.reminders.enabled=false", "--app.email.enabled=false")) {
            var store = context.getBean(PolicyAiRuleDraftStore.class);
            switch (args[0]) {
                case "prepare" -> {
                    var request = store.prepare(new PolicyAiRuleDraftStore.Preparation(UUID.fromString(args[4]), args[1],
                            Long.parseLong(args[2]), args[3], System.getProperty("user.name")));
                    Files.writeString(Path.of(args[5]), context.getBean(ObjectMapper.class).writerWithDefaultPrettyPrinter()
                            .writeValueAsString(request), StandardOpenOption.CREATE_NEW);
                    System.out.println("추출 요청 원문 저장: " + request.id());
                }
                case "complete" -> {
                    var path = Path.of(args[2]);
                    if (Files.size(path) > 131072) throw new IllegalArgumentException("추출 결과는 128KB 이하의 파일을 사용해주세요.");
                    System.out.println(store.complete(UUID.fromString(args[1]), Files.readString(path)));
                }
                case "status" -> System.out.println(store.result(UUID.fromString(args[1])).map(Object::toString).orElse("저장된 결과 없음"));
                default -> throw new IllegalArgumentException("지원하지 않는 명령입니다.");
            }
        }
    }
}
