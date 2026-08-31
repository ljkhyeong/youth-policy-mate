package kr.youthpolicymate.devpreview;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(requiredProperties = {"dataKind", "questionSet", "example"})
public record EligibilityTrialResponse(
        EligibilityExamplesResponse.DataKind dataKind,
        EligibilityTrialRequest.QuestionSetId questionSet,
        EligibilityExamplesResponse.ExampleResponse example
) {}
