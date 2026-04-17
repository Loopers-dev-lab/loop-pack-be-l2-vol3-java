package com.loopers.batch.job.ranking.step;

import com.loopers.batch.job.ranking.MonthlyRankingJobConfig;
import com.loopers.domain.ranking.ProductRankMonthly;
import com.loopers.infrastructure.ranking.ProductRankMonthlyJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Slf4j
@StepScope
@ConditionalOnProperty(name = "spring.batch.job.name", havingValue = MonthlyRankingJobConfig.JOB_NAME)
@RequiredArgsConstructor
@Component
public class MonthlyRankingWriter implements ItemWriter<ProductRankMonthly> {

    private final ProductRankMonthlyJpaRepository monthlyRepository;

    @Override
    public void write(Chunk<? extends ProductRankMonthly> chunk) {
        monthlyRepository.saveAll(chunk.getItems());
        log.debug("월간 랭킹 {}건 저장", chunk.size());
    }
}
