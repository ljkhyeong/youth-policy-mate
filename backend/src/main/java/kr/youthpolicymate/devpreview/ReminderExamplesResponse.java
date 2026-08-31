package kr.youthpolicymate.devpreview;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(requiredProperties = {"dataKind", "examples"})
public record ReminderExamplesResponse(DataKind dataKind, List<ExampleResponse> examples) {
    public enum DataKind { SYNTHETIC }

    @Schema(name = "ReminderExample", requiredProperties = {"id", "label", "description", "result"})
    public record ExampleResponse(String id, String label, String description, ReminderResultResponse result) {}
}
