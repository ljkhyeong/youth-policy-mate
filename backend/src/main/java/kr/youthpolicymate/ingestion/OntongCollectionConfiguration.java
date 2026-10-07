package kr.youthpolicymate.ingestion;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
@Profile("!preview")
public class OntongCollectionConfiguration {
    @Bean
    OntongApiClient ontongApiClient(ObjectMapper mapper, Clock clock) { return new OntongApiClient(mapper, clock); }
}
