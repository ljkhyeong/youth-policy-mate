package kr.youthpolicymate.ingestion;

import kr.youthpolicymate.YouthPolicyMateApplication;
import kr.youthpolicymate.policy.catalog.PolicyRuleStore;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.UUID;

public final class PolicyAiRuleCommand {
    public static void main(String[] args) throws Exception {
        if (!(args.length == 6 && args[0].equals("prepare") || args.length == 3 && args[0].equals("complete")
                || args.length == 2 && (args[0].equals("status") || args[0].equals("generate"))
                || args.length == 5 && args[0].equals("settle") || args.length == 4 && args[0].equals("no-charge")
                || args.length == 1 && (args[0].equals("auto-run") || args[0].equals("auto-status"))))
            throw new IllegalArgumentException("사용법: prepare <정책번호> <개정> <생성 방식> <요청 UUID> <새 JSON 경로> | generate <요청 UUID> | complete <요청 UUID> <결과 JSON 경로> | status <요청 UUID> | settle <요청 UUID> <청구 확인 ID> <확인 시각> <원화 청구액> | no-charge <요청 UUID> <무과금 확인 ID> <확인 시각> | auto-run | auto-status");
        try (var context = YouthPolicyMateApplication.startCommand()) {
            var store = context.getBean(PolicyAiRuleDraftStore.class);
            switch (args[0]) {
                case "auto-run" -> System.out.println(context.getBean(PolicyAiRuleAutoRunner.class).tick());
                case "auto-status" -> context.getBean(PolicyAiRuleAutoStore.class).recent().forEach(System.out::println);
                case "prepare" -> {
                    var request = store.prepare(new PolicyAiRuleDraftStore.Preparation(UUID.fromString(args[4]), args[1],
                            Long.parseLong(args[2]), args[3], System.getProperty("user.name")));
                    Files.writeString(Path.of(args[5]), context.getBean(ObjectMapper.class).writerWithDefaultPrettyPrinter()
                            .writeValueAsString(request), StandardOpenOption.CREATE_NEW);
                    System.out.println("추출 요청 원문 저장: " + request.id());
                }
                case "complete" -> {
                    var path = Path.of(args[2]);
                    if (Files.size(path) > PolicyRuleStore.MAX_DEFINITION_BYTES) throw new IllegalArgumentException("추출 결과는 128KB 이하의 파일을 사용해주세요.");
                    System.out.println(store.complete(UUID.fromString(args[1]), Files.readString(path)));
                }
                case "generate" -> System.out.println(context.getBean(PolicyAiRuleGenerationService.class).generate(UUID.fromString(args[1])));
                case "status" -> System.out.println(context.getBean(PolicyAiRuleGenerationService.class).status(UUID.fromString(args[1])));
                case "settle" -> System.out.println(context.getBean(PolicyAiRuleCallStore.class).settle(UUID.fromString(args[1]),
                        args[2], Instant.parse(args[3]), new BigDecimal(args[4])));
                case "no-charge" -> System.out.println(context.getBean(PolicyAiRuleCallStore.class).releaseNoCharge(UUID.fromString(args[1]),
                        args[2], Instant.parse(args[3])));
                default -> throw new IllegalArgumentException("지원하지 않는 명령입니다.");
            }
        }
    }
}
