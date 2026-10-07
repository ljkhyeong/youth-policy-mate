package kr.youthpolicymate.policy.catalog;

import kr.youthpolicymate.eligibility.EligibilityStatus;
import kr.youthpolicymate.policy.RecruitmentStatus;
import static kr.youthpolicymate.eligibility.ConditionOutcome.*;
import static kr.youthpolicymate.policy.SeoulTime.SEOUL;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
public class PolicyCheckService {
    private static final int PAGE_SIZE = 20;
    private final PolicyCatalogStore store;
    private final Clock clock;

    public PolicyCheckService(PolicyCatalogStore store, Clock clock) { this.store = store; this.clock = clock; }

    // 규칙·건수·목록의 같은 스냅샷은 store.listForCheck의 읽기 트랜잭션이 보장한다.
    public PolicyCheckResponse check(BasicConditions input, int page, String query, PolicyCheckResponse.Sort sort, RecruitmentStatus recruitmentStatus) {
        var now = clock.instant();
        var today = LocalDate.ofInstant(now, SEOUL);
        BasicConditions.checkBirthDate(input.birthDate(), today);
        var policies = store.listForCheck(page, PAGE_SIZE, query, sort, input, recruitmentStatus, now);
        var items = new ArrayList<PolicyCheckResponse.Item>();
        for (var source : policies.items()) {
            var policy = source.policy();
            var raw = source.raw();
            var comparison = source.comparison();
            var age = comparison == null ? null : comparison.age();
            var stated = PolicySourceConditions.from(raw);
            // 연령 항목 이름은 규칙마다 다르므로 목록에서는 "연령"으로 통일한다.
            var checks = List.of(
                    age == null ? statedAge(input, stated, today, text(raw, "addAplyQlfcCndCn", "plcySprtCn"))
                            : new PolicyQuestions.Check("연령", age.providedValue(), age.outcome(), age.explanation(), age.evidence()),
                    new PolicyQuestions.Check("거주", input.district() == null ? "미입력" : "서울특별시 " + input.district().label(), UNKNOWN,
                            "신청 가능한 거주지와 거주 기간·전입 조건은 공식 안내를 확인해주세요.",
                            text(raw, "addAplyQlfcCndCn", "plcyExplnCn")),
                    new PolicyQuestions.Check("취업·학력·소득", input.employmentStatus() == null ? "미입력" : "기본 취업상태 입력됨", UNKNOWN,
                            "주된 취업상태 하나만으로 고용보험·재학·사업자등록·소득 요건을 확인할 수 없어요." + statedOthers(stated),
                            text(raw, "earnEtcCn", "addAplyQlfcCndCn", "plcySprtCn")),
                    new PolicyQuestions.Check("추가 조건과 참여 제한", "추가 확인 필요", UNKNOWN,
                            "공식 공고에서 필수 조건과 예외를 확인해주세요. 이 화면에 없는 제한이 있을 수 있어요.",
                            text(raw, "ptcpPrpTrgtCn", "addAplyQlfcCndCn", "plcySprtCn")));
            items.add(new PolicyCheckResponse.Item(policy.policyNumber(), policy.revision(), policy.content().title(), EligibilityStatus.NEEDS_REVIEW,
                    (input.birthDate() == null ? "지원 내용을 살펴보고 필요한 조건을 추가해보세요." : explanation(comparison, stated, input.birthDate(), today)), policy.content().applicationPeriod(), comparison == null ? policy.sourceUrl() : comparison.sourceUrl(),
                    policy.collectedAt(), checks, source.questionnaireAvailable(), comparison == null ? "" : comparison.ruleVersion(), policy.recruitment()));
        }
        return new PolicyCheckResponse(items, page, policies.total(), (long) page * PAGE_SIZE < policies.total(), now);
    }

    // 검토된 연령 비교가 없으면(생년월일 미입력 포함) 온통청년 표기 범위와 입력한 생년월일의 만 나이를 함께 보여주되 판정하지 않는다.
    private PolicyQuestions.Check statedAge(BasicConditions input, PolicySourceConditions stated, LocalDate today, String evidence) {
        var range = stated.ageText();
        if (input.birthDate() == null) return new PolicyQuestions.Check("연령", "미입력", UNKNOWN, range
                .map(text -> "온통청년 표기 연령은 " + text + "예요. 기준일과 예외는 공고에서 확인해주세요. 생년월일을 추가하면 만 나이를 함께 보여드려요.")
                .orElse("생년월일을 추가하면 확인된 연령 조건을 비교할 수 있어요."), evidence);
        if (range.isEmpty()) return new PolicyQuestions.Check("연령", "생년월일 입력됨", UNKNOWN, "연령 기준일과 제한·예외를 아직 확인하지 못했어요.", evidence);
        return new PolicyQuestions.Check("연령", "만 " + PolicySourceConditions.completedYears(input.birthDate(), today) + "세 (" + today + " · 서울)", UNKNOWN,
                "온통청년 표기 연령은 " + range.orElseThrow() + "예요. 기준일과 예외를 확인하지 못해 추가 확인으로 남겨요.", evidence);
    }

    private String statedOthers(PolicySourceConditions stated) {
        var others = stated.items().stream().filter(item -> !item.label().equals("연령"))
                .map(item -> item.label() + " " + item.value()).toList();
        return others.isEmpty() ? "" : " 온통청년 표기: " + String.join(", ", others) + ".";
    }

    private String explanation(PolicyAgeComparison comparison, PolicySourceConditions stated, LocalDate birth, LocalDate today) {
        if (comparison == null) return stated.ageText()
                .map(range -> "검토한 연령 기준은 아직 없어요. 온통청년 표기 연령은 " + range + "이고 입력한 생년월일은 만 "
                        + PolicySourceConditions.completedYears(birth, today) + "세예요. 기준일과 예외는 공식 공고를 확인해주세요.")
                .orElse("이 정책의 연령 기준은 아직 비교할 수 없어요. 공식 공고를 확인해주세요.");
        var message = switch (comparison.age().outcome()) {
            case MET -> "입력한 생년월일은 연령 조건을 충족해요. 다른 신청 조건은 추가 확인이 필요해요.";
            case NOT_MET -> "입력한 생년월일은 이 공고의 연령 조건을 충족하지 않아요. 적용 기준을 확인해주세요.";
            case UNKNOWN -> comparison.age().explanation();
        };
        return comparison.periodNotice().isEmpty() ? message : message + " " + comparison.periodNotice();
    }

    private String text(JsonNode raw, String... fields) {
        for (var field : fields) {
            var value = raw.path(field);
            if (value.isString() && !value.asString().isBlank()) return value.asString().strip();
        }
        return "이 항목의 원문이 제공되지 않았어요. 공식 신청 안내를 확인해주세요.";
    }
}
