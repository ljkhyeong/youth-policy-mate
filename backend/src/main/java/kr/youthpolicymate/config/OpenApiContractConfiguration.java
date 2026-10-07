package kr.youthpolicymate.config;

import io.swagger.v3.oas.models.media.Schema;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Configuration(proxyBeanMethods = false)
class OpenApiContractConfiguration {
    /** OpenAPI 3.1에서 `@Schema(types = {..., "null"})`로 표시한 참조·enum 필드가 실제 null 응답을 허용하도록 맞춘다. */
    @Bean
    @SuppressWarnings({"rawtypes", "unchecked"})
    OpenApiCustomizer nullableContract() {
        return api -> {
            if (api.getComponents() == null || api.getComponents().getSchemas() == null) return;
            api.getComponents().getSchemas().values().forEach(schema -> {
                Map<String, Schema> properties = schema.getProperties();
                if (properties == null) return;
                properties.replaceAll((name, property) -> {
                    var types = property.getTypes();
                    if (types == null || !types.contains("null")) return property;
                    // $ref와 null 타입을 한 스키마에 두면 참조 대상의 object 제약 때문에 null이 거절된다.
                    if (property.get$ref() != null) return new Schema<>().description(property.getDescription())
                            .anyOf(List.of(new Schema<>().$ref(property.get$ref()), new Schema<>().types(Set.of("null"))));
                    // allowableValues 목록은 고정 크기일 수 있어 복사한 뒤 null을 더한다.
                    if (property.getEnum() != null && !property.getEnum().contains(null)) {
                        var values = new ArrayList<Object>(property.getEnum());
                        values.add(null);
                        property.setEnum(values);
                    }
                    return property;
                });
            });
        };
    }
}
