package kr.youthpolicymate.policy.catalog;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

class PolicySourceConditionsTest {
    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    @Test @DisplayName("연령 제한 표기가 있는 범위만 읽고 제한 없음 표기에 남은 범위나 뒤집힌 범위는 쓰지 않는다")
    void readsStatedAgeOnlyWhenRestricted() {
        assertThat(age(raw().put("sprtTrgtAgeLmtYn", "N").put("sprtTrgtMinAge", "15").put("sprtTrgtMaxAge", "34"))).hasValue("만 15~34세");
        assertThat(age(raw().put("sprtTrgtAgeLmtYn", "N").put("sprtTrgtMinAge", 0).put("sprtTrgtMaxAge", 34))).hasValue("만 34세 이하");
        assertThat(age(raw().put("sprtTrgtAgeLmtYn", "N").put("sprtTrgtMinAge", "19").put("sprtTrgtMaxAge", "0"))).hasValue("만 19세 이상");
        for (var ignored : List.of(raw().put("sprtTrgtAgeLmtYn", "Y").put("sprtTrgtMinAge", "19").put("sprtTrgtMaxAge", "39"),
                raw().put("sprtTrgtAgeLmtYn", "N").put("sprtTrgtMinAge", "40").put("sprtTrgtMaxAge", "19"),
                raw().put("sprtTrgtAgeLmtYn", "N").put("sprtTrgtMinAge", "0").put("sprtTrgtMaxAge", "0"), raw())) {
            assertThat(age(ignored)).isEmpty();
        }
    }

    @Test @DisplayName("연소득 상한과 정의서로 확인한 대상을 담고 무관·제한없음은 숨기며 기타·모르는 코드는 공식 안내 확인으로 남긴다")
    void showsOnlyStatedRestrictions() {
        var stated = PolicySourceConditions.from(raw().put("earnCndSeCd", "0043002").put("earnMaxAmt", "3692")
                .put("jobCd", "0013001,0013003,0013006,0013009").put("schoolCd", "0049004,0049005"));
        assertThat(stated.items()).extracting(PolicySourceCondition::label, PolicySourceCondition::value).containsExactly(
                tuple("소득", "연소득 3,692만 원 이하"), tuple("취업 상태", "재직자·미취업자·(예비)창업자 등 · 공식 안내 확인"),
                tuple("학력", "대학 재학 등 · 공식 안내 확인"));
        assertThat(PolicySourceConditions.from(raw().put("earnCndSeCd", "0043001").put("earnMaxAmt", "3500")
                .put("jobCd", "0013010").put("schoolCd", "0049010")).items()).isEmpty();
        assertThat(PolicySourceConditions.from(raw().put("jobCd", "0013003")).items())
                .extracting(PolicySourceCondition::value).containsExactly("미취업자");
        assertThat(PolicySourceConditions.from(raw().put("jobCd", "0013009").put("schoolCd", "0049007")).items())
                .extracting(PolicySourceCondition::label, PolicySourceCondition::value)
                .containsExactly(tuple("취업 상태", "조건 표기 있음 · 공식 안내 확인"), tuple("학력", "조건 표기 있음 · 공식 안내 확인"));
    }

    private static java.util.Optional<String> age(ObjectNode raw) { return PolicySourceConditions.from(raw).ageText(); }

    private static ObjectNode raw() { return MAPPER.createObjectNode(); }
}
