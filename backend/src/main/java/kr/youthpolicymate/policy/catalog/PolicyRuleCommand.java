package kr.youthpolicymate.policy.catalog;

import kr.youthpolicymate.YouthPolicyMateApplication;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import tools.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.util.UUID;

public final class PolicyRuleCommand {
    public static void main(String[] args) throws Exception {
        if (!(args.length == 1 && args[0].equals("status") || args.length == 3 && (args[0].equals("draft") || args[0].equals("publish") || args[0].equals("export"))))
            throw new IllegalArgumentException("사용법: status | draft <JSON 경로> <변경 사유> | publish <초안 ID> <직전 버전 또는 none> | export <버전 ID> <새 JSON 경로>");
        var app = new SpringApplication(YouthPolicyMateApplication.class);
        app.setAdditionalProfiles("local");
        app.setWebApplicationType(WebApplicationType.NONE);
        try (var context = app.run("--spring.config.import=optional:file:.env[.properties]", "--app.ontong.schedule.enabled=false",
                "--app.reminders.enabled=false", "--app.email.enabled=false")) {
            var store = context.getBean(PolicyRuleStore.class);
            var actor = System.getProperty("user.name");
            switch (args[0]) {
                case "status" -> store.status().forEach(System.out::println);
                case "export" -> Files.writeString(Path.of(args[2]), context.getBean(ObjectMapper.class)
                        .writerWithDefaultPrettyPrinter().writeValueAsString(store.definition(UUID.fromString(args[1]))), StandardOpenOption.CREATE_NEW);
                case "draft" -> {
                    var definition = context.getBean(ObjectMapper.class).readValue(Files.readString(Path.of(args[1])), PolicyRuleDefinition.class);
                    System.out.println("등록한 초안: " + store.draft(definition, actor, args[2]));
                }
                case "publish" -> { store.publish(UUID.fromString(args[1]), args[2], actor); System.out.println("규칙을 적용했습니다."); }
                default -> throw new IllegalArgumentException("지원하지 않는 명령입니다.");
            }
        }
    }
}
