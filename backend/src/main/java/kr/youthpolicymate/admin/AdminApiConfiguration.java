package kr.youthpolicymate.admin;

import io.swagger.v3.oas.models.media.Schema;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Configuration(proxyBeanMethods = false)
@Profile("!preview")
class AdminApiConfiguration {
    @Bean
    OpenApiCustomizer policyRuleContract() {
        return api -> {
            var definition = api.getComponents().getSchemas().get("PolicyRuleDefinition");
            if (definition == null) return;
            Map.of("ageBinding", "PolicyRuleAgeBinding", "birthBinding", "PolicyRuleBirthBinding",
                    "periodNotice", "PolicyRulePeriodNotice", "remainingVariant", "PolicyRuleRemainingVariant")
                    .forEach((field, type) -> definition.addProperty(field, new Schema<>().anyOf(List.of(
                            new Schema<>().$ref("#/components/schemas/" + type), new Schema<>().types(Set.of("null"))))));
        };
    }

    @Bean
    OpenApiCustomizer collectionExceptionContract() {
        return api -> {
            var detail = api.getComponents().getSchemas().get("CollectionExceptionDetail");
            if (detail == null) return;
            // object 참조와 null을 별도 분기로 둬 실제 null 응답도 계약에 맞춘다.
            detail.addProperty("currentPolicy", new Schema<>().description("같은 정책번호의 조회 시점 공개 내용. 없으면 null")
                    .anyOf(List.of(new Schema<>().$ref("#/components/schemas/CollectionExceptionCurrentPolicy"),
                            new Schema<>().types(Set.of("null")))));
            api.getComponents().getSchemas().get("CollectionExceptionCurrentPolicy")
                    .addProperty("previousRevision", new Schema<>().description("같은 정책의 직전 내부 개정. 없으면 null")
                            .anyOf(List.of(new Schema<>().$ref("#/components/schemas/CollectionExceptionRevision"),
                                    new Schema<>().types(Set.of("null")))));
        };
    }
}
