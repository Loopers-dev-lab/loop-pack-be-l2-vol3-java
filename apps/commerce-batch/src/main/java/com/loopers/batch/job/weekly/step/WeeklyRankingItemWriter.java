package com.loopers.batch.job.weekly.step;

import com.loopers.batch.job.weekly.WeeklyRankingJobConfig;
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
 * 주간 랭킹 MV 테이블 Writer.
 *
 * Reader SQL 이 score DESC 로 정렬되어 있으므로 Chunk 의 순서가 곧 랭킹 순서다.
 * 이 Writer 는 Chunk 순서를 기반으로 rank(1, 2, 3...) 를 부여하고
 * DELETE + INSERT 방식으로 mv_product_rank_weekly 를 갱신한다.
 *
 * baseDate 결정 규칙:
 *   MV 에는 "targetDate - 1일" 을 base_date 로 저장한다.
 *   예: targetDate = 2026-04-10 → base_date = 2026-04-09 (어제)
 *   API 는 date 파라미터 생략 시 어제를 기준으로 조회하므로 일관성이 유지된다.
 *
 * @StepScope 로 선언하여 Job 실행마다 새 인스턴스를 생성하고
 * targetDate JobParameter 를 올바르게 바인딩한다.
 */
@StepScope
@ConditionalOnProperty(name = "spring.batch.job.name", havingValue = WeeklyRankingJobConfig.JOB_NAME)
@RequiredArgsConstructor
@Component
public class WeeklyRankingItemWriter implements ItemWriter<ProductMetricsAggregate> {

    // JobParameter 에서 주입. targetDate - 1일이 MV 의 base_date 가 된다.
    @Value("#{jobParameters['targetDate']}")
    private LocalDate targetDate;

    private final MvProductRankRepository mvProductRankRepository;

    @Override
    public void write(Chunk<? extends ProductMetricsAggregate> chunk) {
        // API 는 어제를 기준으로 랭킹을 조회하므로 base_date = targetDate - 1
        LocalDate baseDate = targetDate.minusDays(1);

        List<MvProductRankRow> rows = new ArrayList<>();
        int rank = 1;
        // SQL ORDER BY score DESC 가 이미 적용되어 있으므로 순서 = rank.
        // WeeklyRankingJobConfig.TOP_N = chunk size = LIMIT 이 일치해야 단일 Chunk 가 보장된다.
        // 다중 Chunk 가 되면 매 write() 호출마다 rank 가 1 부터 재시작되므로 반드시 단일 Chunk 를 유지할 것.
        for (ProductMetricsAggregate item : chunk.getItems()) {
            rows.add(new MvProductRankRow(item.productId(), rank++, item.score()));
        }

        mvProductRankRepository.replaceWeeklyRanking(baseDate, rows);
    }
}
