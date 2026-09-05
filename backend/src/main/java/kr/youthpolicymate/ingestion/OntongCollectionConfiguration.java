package kr.youthpolicymate.ingestion;

import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.interceptor.DefaultTransactionAttribute;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.util.UUID;

@Configuration(proxyBeanMethods = false)
@Profile("!preview")
public class OntongCollectionConfiguration {
    @Bean
    OntongRequestLimits ontongRequestLimits(Environment environment) {
        return new OntongRequestLimits(environment.getProperty("ONTONG_COLLECTION_DAILY_LIMIT", Integer.class, 0),
                environment.getProperty("ONTONG_COLLECTION_INTERVAL_SECONDS", Long.class, 0L));
    }

    @Bean
    OntongApiClient ontongApiClient(ObjectMapper mapper, Clock clock) { return new OntongApiClient(mapper, clock); }

    @Bean
    OntongCollectionService ontongCollectionService(OntongCollectionStore store, OntongApiClient client, ObjectMapper mapper) {
        return new OntongCollectionService(store, client, mapper);
    }

    @Bean
    Job limitedOntongCollection(JobRepository repository, PlatformTransactionManager transactionManager,
                               OntongCollectionService service, Environment environment) {
        var step = new StepBuilder("fetchOrReprocessOntongPage", repository)
                .tasklet((contribution, context) -> {
                    try {
                        var parameters = contribution.getStepExecution().getJobParameters();
                        var runId = UUID.fromString(parameters.getString("runId"));
                        var mode = parameters.getString("mode");
                        if ("fetch".equals(mode)) service.fetch(runId, Math.toIntExact(parameters.getLong("page")),
                                environment.getProperty("ONTONG_API_KEY", ""));
                        else if ("receive".equals(mode)) service.receive(runId, environment.getProperty("ONTONG_API_KEY", ""));
                        else if (!"replay".equals(mode)) throw new OntongApiClient.Failure("INVALID_MODE");
                        service.applyStored(runId);
                        return RepeatStatus.FINISHED;
                    } catch (OntongApiClient.Failure failure) { throw failure; }
                    catch (RuntimeException exception) { throw new OntongApiClient.Failure("COLLECTION_STORE_FAILED"); }
                }, transactionManager)
                // 외부 응답 대기와 항목 순회는 트랜잭션 밖에서, 각 항목 저장만 짧게 커밋한다.
                .transactionAttribute(new DefaultTransactionAttribute(TransactionDefinition.PROPAGATION_NOT_SUPPORTED))
                .build();
        return new JobBuilder("limitedOntongCollection", repository).start(step).build();
    }
}
