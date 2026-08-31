package kr.youthpolicymate.devpreview;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import kr.youthpolicymate.policy.ApplicationPeriod;
import kr.youthpolicymate.policy.RecruitmentSchedule;
import kr.youthpolicymate.schedule.DeadlineReminderCandidates;
import org.springframework.context.annotation.Profile;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

@RestController
@Profile("preview")
public class ReminderPreviewController {
    // 현재 날짜가 바뀌어도 같은 서버 계산을 점검할 수 있도록 인공 자료의 시계를 고정한다.
    private static final Clock SAMPLE_CLOCK = Clock.fixed(Instant.parse("2026-08-30T15:30:00Z"), ZoneId.of("Asia/Seoul"));

    @GetMapping(value = "/api/dev/reminder-examples", produces = "application/json")
    @Operation(operationId = "listDevelopmentReminderExamples", summary = "개발 전용 마감 알림 계산 예시 조회",
            description = "입력·저장·발송 없이 고정 인공 신청기간을 서버 도메인 모델로 계산한다. preview 프로필에서만 제공한다.")
    @ApiResponse(responseCode = "200", description = "고정 인공 자료와 서버 계산 결과. 예약 완료를 뜻하지 않는다.")
    @ApiResponse(responseCode = "403", description = "preview 프로필이 아닌 경우 접근 거부. 오류 본문은 사용하지 않는다.", content = @Content)
    public ResponseEntity<ReminderExamplesResponse> examples() {
        var examples = List.of(
                example("dates", "날짜형 · 오늘 후보", "서버의 예시 오늘은 2026-08-31입니다. 날짜형 마감과 오늘·미래 후보를 계산합니다.",
                        new ApplicationPeriod.Dates(LocalDate.parse("2026-08-20"), LocalDate.parse("2026-09-07")), "1",
                        "인공 문구: 신청기간은 2026년 8월 20일부터 9월 7일까지이며 접수 시각은 기재하지 않았습니다."),
                example("times", "시각형 · 시간대", "UTC 9월 6일 18:00과 같은 순간의 서울 날짜는 9월 7일입니다.",
                        new ApplicationPeriod.Times(ZonedDateTime.parse("2026-08-20T00:00:00Z[UTC]"), ZonedDateTime.parse("2026-09-06T18:00:00Z[UTC]")), "2",
                        "인공 문구: 2026-08-20 00:00 UTC 시작, 2026-09-06 18:00 UTC 마감."),
                example("unresolved", "마감일 미확인", "원문 날짜가 충돌하여 한쪽을 임의로 선택하지 않습니다.",
                        new ApplicationPeriod.Unresolved("신청기간 항목은 9월 7일, 본문은 9월 10일로 서로 다릅니다."), "3",
                        "인공 문구: 신청기간 항목 ‘9월 7일 마감’, 본문 ‘9월 10일 마감’."),
                example("rolling", "상시 모집", "고정 마감 날짜가 없습니다. 현재 접수 여부는 별도 확인 사항입니다.",
                        new ApplicationPeriod.Rolling(), "4", "인공 문구: 상시 모집."),
                example("exhausted", "소진 시 종료", "예산 소진 날짜와 현재 소진 여부를 추정하지 않습니다.",
                        new ApplicationPeriod.UntilExhausted(), "5", "인공 문구: 예산 소진 시 종료."),
                example("closed", "모집 마감", "확인된 종료일이 지났습니다. 원래 기간은 보존하고 후보는 만들지 않습니다.",
                        new ApplicationPeriod.Dates(LocalDate.parse("2026-08-20"), LocalDate.parse("2026-08-30")), "6",
                        "인공 문구: 신청기간은 2026년 8월 20일부터 8월 30일까지입니다."),
                example("no-remaining", "후보 날짜 모두 지남", "아직 날짜 기준 신청기간이지만 D-7·D-3·D-1은 모두 지났습니다.",
                        new ApplicationPeriod.Dates(LocalDate.parse("2026-08-20"), LocalDate.parse("2026-08-31")), "7",
                        "인공 문구: 신청기간은 2026년 8월 20일부터 8월 31일까지입니다.")
        );
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new ReminderExamplesResponse(ReminderExamplesResponse.DataKind.SYNTHETIC, examples));
    }

    private ReminderExamplesResponse.ExampleResponse example(String id, String label, String description,
            ApplicationPeriod period, String revision, String excerpt) {
        var schedule = new RecruitmentSchedule("sample-deadline-policy", "sample-revision-" + revision, period,
                "sample-period-source · 실제 정책 원문 아님", "인공 자료 · 신청기간 항목", Optional.of(excerpt));
        return new ReminderExamplesResponse.ExampleResponse(id, label, description,
                ReminderResultResponse.from(DeadlineReminderCandidates.calculate(schedule, SAMPLE_CLOCK)));
    }
}
