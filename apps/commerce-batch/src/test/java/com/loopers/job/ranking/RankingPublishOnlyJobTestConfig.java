package com.loopers.job.ranking;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.Step;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * 스테이징 검증 실패 시 MV 비교를 위해 publish Step 단독 Job을 테스트에서 실행한다.
 */
@TestConfiguration
public class RankingPublishOnlyJobTestConfig {

    public static final String BEAN_NAME = "rankingPublishOnlyJob";

    @Bean(BEAN_NAME)
    public Job rankingPublishOnlyJob(
            JobRepository jobRepository,
            @Qualifier("rankingPublish") Step rankingPublishStep
    ) {
        return new JobBuilder("rankingPublishOnlyJob", jobRepository)
                .start(rankingPublishStep)
                .build();
    }
}
