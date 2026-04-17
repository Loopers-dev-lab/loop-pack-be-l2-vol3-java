package com.loopers.batch.job.ranking.step;

import com.loopers.batch.job.ranking.WeeklyRankingJobConfig;
import com.loopers.domain.ranking.ProductRankWeekly;
import com.loopers.infrastructure.ranking.ProductRankWeeklyJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Slf4j
@StepScope
@ConditionalOnProperty(name = "spring.batch.job.name", havingValue = WeeklyRankingJobConfig.JOB_NAME)
@RequiredArgsConstructor
@Component
public class WeeklyRankingWriter implements ItemWriter<ProductRankWeekly> {

    private final ProductRankWeeklyJpaRepository weeklyRepository;

    @Override
    public void write(Chunk<? extends ProductRankWeekly> chunk) {
        weeklyRepository.saveAll(chunk.getItems());
        log.debug("주간 랭킹 {}건 저장", chunk.size());
    }
}
