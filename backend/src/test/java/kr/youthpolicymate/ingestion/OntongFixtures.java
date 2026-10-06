package kr.youthpolicymate.ingestion;

import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** 점검 도구 출력 형식의 목록 캡처에서 테스트에 쓰는 온통청년 응답 본문을 꺼낸다. */
public final class OntongFixtures {
    private OntongFixtures() {}

    public static String listBody(ObjectMapper mapper) {
        try {
            var capture = mapper.readTree(Files.readString(Path.of("src/test/resources/ontong/list-capture.json")));
            return capture.path("response").path("rawBody").asString();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
