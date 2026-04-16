package com.loopers.batch.job.monthly.step;

import com.loopers.batch.job.monthly.MonthlyRankingJobConfig;
import com.loopers.domain.ranking.MvProductRankRepository;
import com.loopers.domain.ranking.MvProductRankRow;
import com.loopers.domain.ranking.ProductMetricsAggregate;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 월간 랭킹 MV 테이블 Writer.
 *
 * write() 는 청크 단위로 row 를 누적하고, afterStep() 에서 replaceMonthlyRanking() 을 1회 호출한다.
 * 이 방식은 chunk size 가 LIMIT 보다 작아 다중 청크가 발생해도 rank 연속성과 단일 치환을 보장한다.
 *
 * baseDate = targetDate - 1일. API 의 기본 조회 기준일과 일치한다.
 */
@StepScope
@ConditionalOnProperty(name = "spring.batch.job.name", havingValue = MonthlyRankingJobConfig.JOB_NAME)
@RequiredArgsConstructor
@Component
public class MonthlyRankingItemWriter implements ItemWriter<ProductMetricsAggregate>, StepExecutionListener {

    @Value("#{jobParameters['targetDate']}")
    private LocalDate targetDate;

    private final MvProductRankRepository mvProductRankRepository;

    private final List<MvProductRankRow> accumulated = new ArrayList<>();
    private int nextRank = 1;

    @Override
    public void write(Chunk<? extends ProductMetricsAggregate> chunk) {
        if (targetDate == null) {
            throw new IllegalStateException("JobParameter 'targetDate' is required");
        }
        for (ProductMetricsAggregate item : chunk.getItems()) {
            accumulated.add(new MvProductRankRow(item.productId(), nextRank++, item.score()));
        }
    }

    @Override
    public ExitStatus afterStep(StepExecution stepExecution) {
        if (targetDate == null) {
            return stepExecution.getExitStatus();
        }
        mvProductRankRepository.replaceMonthlyRanking(targetDate.minusDays(1), accumulated);
        return stepExecution.getExitStatus();
    }
}
