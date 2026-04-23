package com.loopers.batch.job.ranking.weekly.step;

import com.loopers.batch.job.ranking.ScoredProductMetrics;
import com.loopers.batch.job.ranking.weekly.WeeklyRankingJobConfig;
import com.loopers.domain.ranking.ProductRankWeeklyModel;
import com.loopers.infrastructure.ranking.ProductRankWeeklyJpaRepository;
import jakarta.annotation.Nonnull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.IsoFields;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Slf4j
@StepScope
@ConditionalOnProperty(name = "spring.batch.job.name", havingValue = WeeklyRankingJobConfig.JOB_NAME)
@RequiredArgsConstructor
@Component
public class WeeklyRankingWriter implements ItemWriter<ScoredProductMetrics>, StepExecutionListener {

    private static final int TOP_LIMIT = 100;

    private final ProductRankWeeklyJpaRepository productRankWeeklyJpaRepository;
    private final List<ScoredProductMetrics> allItems = new ArrayList<>();

    @Value("#{jobParameters['requestDate']}")
    private LocalDate requestDate;

    @Override
    public void write(@Nonnull Chunk<? extends ScoredProductMetrics> chunk) {
        allItems.addAll(chunk.getItems());
    }

    @Transactional
    @Override
    public ExitStatus afterStep(@Nonnull StepExecution stepExecution) {
        if (stepExecution.getExitStatus().getExitCode().equals(ExitStatus.FAILED.getExitCode())) {
            return ExitStatus.FAILED;
        }

        String yearWeek = toYearWeek(requestDate);

        List<ScoredProductMetrics> sorted = allItems.stream()
            .sorted(Comparator.comparingDouble(ScoredProductMetrics::score).reversed())
            .limit(TOP_LIMIT)
            .toList();

        productRankWeeklyJpaRepository.deleteAllByYearWeek(yearWeek);

        List<ProductRankWeeklyModel> entities = new ArrayList<>();
        for (int i = 0; i < sorted.size(); i++) {
            ScoredProductMetrics item = sorted.get(i);
            entities.add(new ProductRankWeeklyModel(
                item.productId(),
                i + 1,
                item.score(),
                item.viewCount(),
                item.likeCount(),
                item.salesQuantity(),
                yearWeek
            ));
        }

        productRankWeeklyJpaRepository.saveAll(entities);
        log.info("주간 랭킹 저장 완료: yearWeek={}, count={}", yearWeek, entities.size());

        return ExitStatus.COMPLETED;
    }

    private String toYearWeek(LocalDate date) {
        int year = date.get(IsoFields.WEEK_BASED_YEAR);
        int week = date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
        return String.format("%d-W%02d", year, week);
    }
}
