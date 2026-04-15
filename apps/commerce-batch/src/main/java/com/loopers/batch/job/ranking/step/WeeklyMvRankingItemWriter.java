package com.loopers.batch.job.ranking.step;

import com.loopers.batch.job.ranking.RankingWeeklyJobConfig;
import com.loopers.infrastructure.ranking.MvProductRankWeeklyEntity;
import com.loopers.infrastructure.ranking.MvProductRankWeeklyJpaRepository;
import java.time.DayOfWeek;
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
@ConditionalOnProperty(name = "spring.batch.job.name", havingValue = RankingWeeklyJobConfig.JOB_NAME)
@RequiredArgsConstructor
@Component
public class WeeklyMvRankingItemWriter implements ItemWriter<RankedProductDto> {

    private final MvProductRankWeeklyJpaRepository weeklyRepository;

    @Value("#{jobParameters['targetDate']}")
    private String targetDate;

    @Override
    public void write(Chunk<? extends RankedProductDto> chunk) {
        String yearWeek = LocalDate.parse(targetDate, DateTimeFormatter.BASIC_ISO_DATE)
                .with(DayOfWeek.MONDAY)
                .format(DateTimeFormatter.BASIC_ISO_DATE);

        for (RankedProductDto item : chunk.getItems()) {
            weeklyRepository.findById(item.getProductId())
                    .ifPresentOrElse(
                            existing -> existing.update(item.getScore(), yearWeek, 0),
                            () -> weeklyRepository.save(new MvProductRankWeeklyEntity(item.getProductId(), item.getScore(), yearWeek, 0))
                    );
        }
    }
}
