package kr.youthpolicymate.ingestion;

import kr.youthpolicymate.YouthPolicyMateApplication;
import kr.youthpolicymate.policy.catalog.PolicyCatalogStore;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Files;
import java.nio.file.Path;

/** 확인한 소량의 로컬 캡처를 적재하는 개발 명령. 자동 수집기나 공개 쓰기 API가 아니다. */
public final class PolicyCaptureImport {
    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("정책 목록 캡처 파일 하나를 지정하세요.");
            System.exit(1);
        }
        OntongPolicyCapture.Parsed capture;
        var parser = new OntongPolicyCapture(JsonMapper.builder().build());
        try {
            var path = Path.of(args[0]);
            if (Files.size(path) > 2 * 1024 * 1024) throw new IllegalArgumentException();
            capture = parser.parse(Files.readString(path));
        } catch (Exception exception) {
            System.err.println("목록 캡처를 읽지 못했습니다. 파일과 정상 목록 응답 여부를 확인하세요.");
            System.exit(1);
            return;
        }
        var application = new SpringApplication(YouthPolicyMateApplication.class);
        application.setAdditionalProfiles("local");
        application.setWebApplicationType(WebApplicationType.NONE);
        int failures = 0;
        try (var context = application.run("--spring.config.import=optional:file:.env[.properties]")) {
            var store = context.getBean(PolicyCatalogStore.class);
            for (int index = 0; index < capture.items().size(); index++) {
                try {
                    var item = parser.item(capture.items().get(index));
                    var result = store.importPolicy(item.number(), item.content(), item.rawPolicy(),
                            capture.capturedAt(), capture.hash(), item.contentHash());
                    System.out.println("정책 " + item.number() + ": " + switch (result) {
                        case APPLIED -> "새 개정 저장";
                        case UNCHANGED -> "같은 내용 확인";
                        case REPLAYED -> "이미 반영한 캡처";
                        case STALE -> "오래된 캡처 보관·현재 내용 유지";
                    });
                } catch (RuntimeException exception) {
                    failures++;
                    System.err.println("목록 " + (index + 1) + "번째 항목 저장 실패. 원본은 캡처 파일에 남아 있습니다.");
                }
            }
            System.out.println("목록 " + capture.items().size() + "건 처리, 실패 " + failures + "건");
        }
        if (failures > 0) System.exit(1);
    }
}
