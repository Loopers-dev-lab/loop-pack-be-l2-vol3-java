package com.loopers.batch.job.ranking.monthly.step;

import com.loopers.batch.job.ranking.ScoredProductMetrics;
import com.loopers.batch.job.ranking.monthly.MonthlyRankingJobConfig;
import com.loopers.domain.ranking.ProductRankMonthlyModel;
import com.loopers.infrastructure.ranking.ProductRankMonthlyJpaRepository;
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
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Slf4j
@StepScope
@ConditionalOnProperty(name = "spring.batch.job.name", havingValue = MonthlyRankingJobConfig.JOB_NAME)
@RequiredArgsConstructor
@Component
public class MonthlyRankingWriter implements ItemWriter<ScoredProductMetrics>, StepExecutionListener {

    private static final int TOP_LIMIT = 100;
    private static final DateTimeFormatter YEAR_MONTH_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM");

    private final ProductRankMonthlyJpaRepository productRankMonthlyJpaRepository;
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

        String yearMonth = requestDate.format(YEAR_MONTH_FORMATTER);

        List<ScoredProductMetrics> sorted = allItems.stream()
            .sorted(Comparator.comparingDouble(ScoredProductMetrics::score).reversed())
            .limit(TOP_LIMIT)
            .toList();

        productRankMonthlyJpaRepository.deleteAllByYearMonth(yearMonth);

        List<ProductRankMonthlyModel> entities = new ArrayList<>();
        for (int i = 0; i < sorted.size(); i++) {
            ScoredProductMetrics item = sorted.get(i);
            entities.add(new ProductRankMonthlyModel(
                item.productId(),
                i + 1,
                item.score(),
                item.viewCount(),
                item.likeCount(),
                item.salesQuantity(),
                yearMonth
            ));
        }

        productRankMonthlyJpaRepository.saveAll(entities);
        log.info("월간 랭킹 저장 완료: yearMonth={}, count={}", yearMonth, entities.size());

        return ExitStatus.COMPLETED;
    }
}
