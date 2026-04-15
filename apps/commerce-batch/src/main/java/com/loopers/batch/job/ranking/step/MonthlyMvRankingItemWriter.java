package com.loopers.batch.job.ranking.step;

import com.loopers.batch.job.ranking.RankingMonthlyJobConfig;
import com.loopers.infrastructure.ranking.MvProductRankMonthlyEntity;
import com.loopers.infrastructure.ranking.MvProductRankMonthlyJpaRepository;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@StepScope
@ConditionalOnProperty(name = "spring.batch.job.name", havingValue = RankingMonthlyJobConfig.JOB_NAME)
@RequiredArgsConstructor
@Component
public class MonthlyMvRankingItemWriter implements ItemWriter<RankedProductDto> {

    private final MvProductRankMonthlyJpaRepository monthlyRepository;

    @Value("#{jobParameters['targetDate']}")
    private String targetDate;

    @Override
    public void write(Chunk<? extends RankedProductDto> chunk) {
        String yearMonth = LocalDate.parse(targetDate, DateTimeFormatter.BASIC_ISO_DATE)
                .format(RankingMonthlyJobConfig.YEAR_MONTH_FORMAT);

        for (RankedProductDto item : chunk.getItems()) {
            monthlyRepository.findById(item.getProductId())
                    .ifPresentOrElse(
                            existing -> existing.update(item.getScore(), yearMonth, 0),
                            () -> monthlyRepository.save(new MvProductRankMonthlyEntity(item.getProductId(), item.getScore(), yearMonth, 0))
                    );
        }
    }
}
