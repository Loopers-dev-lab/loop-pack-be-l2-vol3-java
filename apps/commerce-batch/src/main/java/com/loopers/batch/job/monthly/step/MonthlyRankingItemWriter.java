package com.loopers.batch.job.monthly.step;

import com.loopers.batch.job.monthly.MonthlyRankingJobConfig;
import com.loopers.domain.ranking.MvProductRankRepository;
import com.loopers.domain.ranking.MvProductRankRow;
import com.loopers.domain.ranking.ProductMetricsAggregate;
import lombok.RequiredArgsConstructor;
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
 * Reader SQL 이 score DESC 로 정렬되어 있으므로 Chunk 의 순서가 곧 랭킹 순서다.
 * rank(1, 2, 3...) 를 부여하고 DELETE + INSERT 방식으로 mv_product_rank_monthly 를 갱신한다.
 *
 * WeeklyRankingItemWriter 와 로직이 동일하며, 대상 저장소 메서드만 다르다.
 *
 * baseDate = targetDate - 1일 (어제).
 * API 는 date 파라미터 생략 시 어제를 기준으로 조회하므로 일관성이 유지된다.
 */
@StepScope
@ConditionalOnProperty(name = "spring.batch.job.name", havingValue = MonthlyRankingJobConfig.JOB_NAME)
@RequiredArgsConstructor
@Component
public class MonthlyRankingItemWriter implements ItemWriter<ProductMetricsAggregate> {

    // JobParameter 에서 주입. targetDate - 1일이 MV 의 base_date 가 된다.
    @Value("#{jobParameters['targetDate']}")
    private LocalDate targetDate;

    private final MvProductRankRepository mvProductRankRepository;

    @Override
    public void write(Chunk<? extends ProductMetricsAggregate> chunk) {
        LocalDate baseDate = targetDate.minusDays(1);

        List<MvProductRankRow> rows = new ArrayList<>();
        int rank = 1;
        // SQL ORDER BY score DESC 가 이미 적용되어 있으므로 순서 = rank.
        // MonthlyRankingJobConfig.TOP_N = chunk size = LIMIT 이 일치해야 단일 Chunk 가 보장된다.
        // 다중 Chunk 가 되면 매 write() 호출마다 rank 가 1 부터 재시작되므로 반드시 단일 Chunk 를 유지할 것.
        for (ProductMetricsAggregate item : chunk.getItems()) {
            rows.add(new MvProductRankRow(item.productId(), rank++, item.score()));
        }

        mvProductRankRepository.replaceMonthlyRanking(baseDate, rows);
    }
}
