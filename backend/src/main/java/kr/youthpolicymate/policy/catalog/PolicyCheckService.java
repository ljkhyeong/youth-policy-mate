package kr.youthpolicymate.policy.catalog;

import kr.youthpolicymate.eligibility.*;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@Profile("!preview")
public class PolicyCheckService {
    private final PolicyCatalogStore store;
    private final Clock clock;

    public PolicyCheckService(PolicyCatalogStore store, Clock clock) { this.store = store; this.clock = clock; }

    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public PolicyCheckResponse check(BasicConditions input, int page) {
        var now = clock.instant();
        input.validate(LocalDate.ofInstant(now, ZoneId.of("Asia/Seoul")));
        var policies = store.list("", page, 20, false, now);
        var items = new ArrayList<PolicyCheckResponse.Item>();
        for (var summary : policies.items()) {
            var policy = store.find(summary.policyNumber()).orElseThrow(PolicyNotFoundException::new);
            var raw = store.source(policy.policyNumber()).orElseThrow(PolicyNotFoundException::new);
            var checks = List.of(
                    new PolicyCheckResponse.Check("연령", "생년월일 입력됨",
                            "연령을 계산할 정책 기준일과 제한·예외가 확인되지 않았어요.",
                            text(raw, "addAplyQlfcCndCn", "plcySprtCn")),
                    new PolicyCheckResponse.Check("거주", "서울특별시 " + input.district(),
                            "신청 가능한 거주지와 거주 기간·전입 조건은 공식 안내를 확인해주세요.",
                            text(raw, "addAplyQlfcCndCn", "plcyExplnCn")),
                    new PolicyCheckResponse.Check("취업·학력·소득", "기본 취업상태 입력됨",
                            "주된 취업상태 하나만으로 고용보험·재학·사업자등록·소득 요건을 확인할 수 없어요.",
                            text(raw, "earnEtcCn", "addAplyQlfcCndCn", "plcySprtCn")),
                    new PolicyCheckResponse.Check("추가 조건과 참여 제한", "추가 확인 필요",
                            "본문과 공식 신청처의 필수 조건·예외를 함께 확인해야 해요. 원문 누락은 제한 없음이 아니에요.",
                            text(raw, "ptcpPrpTrgtCn", "addAplyQlfcCndCn", "plcySprtCn")));
            var evidence = new SourceEvidence(policy.sourceUrl(), "온통청년 정책 원문", Optional.of(checks.getLast().evidence()));
            var decision = new EligibilityDecision(new EvaluationBasis(policy.policyNumber(), Long.toString(policy.revision()),
                    "source-review-v1", now), PolicyReview.incomplete(List.of(new PolicyReview.PendingIssue(
                    "실제 정책의 기준일과 필수 조건·예외가 판정 규칙으로 확인되지 않았습니다.", evidence))), List.of());
            items.add(new PolicyCheckResponse.Item(policy.policyNumber(), policy.revision(), policy.content().title(), decision.status(),
                    "신청 자격을 확정할 수 없어요. 아래 원문과 추가 확인할 항목을 살펴보세요.",
                    policy.content().applicationPeriod(), policy.sourceUrl(), policy.collectedAt(), checks, summary.questionnaireAvailable()));
        }
        return new PolicyCheckResponse(items, page, policies.total(), policies.hasNext(), now);
    }

    private String text(JsonNode raw, String... fields) {
        for (var field : fields) {
            var value = raw.path(field);
            if (value.isString() && !value.asString().isBlank()) return value.asString().strip();
        }
        return "이 항목의 원문이 제공되지 않았어요. 공식 신청 안내를 확인해주세요.";
    }
}
