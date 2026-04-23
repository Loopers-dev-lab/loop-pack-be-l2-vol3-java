package com.loopers.batch.job.ranking.weekly.step;

import com.loopers.batch.job.ranking.weekly.WeeklyRankingJobConfig;
import com.loopers.domain.ranking.BatchProductMetricsModel;
import jakarta.persistence.EntityManagerFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.item.database.JpaPagingItemReader;
import org.springframework.batch.item.database.builder.JpaPagingItemReaderBuilder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@ConditionalOnProperty(name = "spring.batch.job.name", havingValue = WeeklyRankingJobConfig.JOB_NAME)
@RequiredArgsConstructor
@Component
public class WeeklyRankingReader {

    private static final int PAGE_SIZE = 100;

    private final EntityManagerFactory entityManagerFactory;

    public JpaPagingItemReader<BatchProductMetricsModel> reader() {
        return new JpaPagingItemReaderBuilder<BatchProductMetricsModel>()
            .name("weeklyRankingReader")
            .entityManagerFactory(entityManagerFactory)
            .queryString("SELECT m FROM BatchProductMetricsModel m WHERE m.deletedAt IS NULL ORDER BY m.id ASC")
            .pageSize(PAGE_SIZE)
            .build();
    }
}
