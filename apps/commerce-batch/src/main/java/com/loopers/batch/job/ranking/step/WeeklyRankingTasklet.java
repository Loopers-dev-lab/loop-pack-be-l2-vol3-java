package com.loopers.batch.job.ranking.step;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Objects;

import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.loopers.batch.job.ranking.WeeklyRankingJobConfig;
import com.loopers.domain.metrics.ProductMetricsRepository;
import com.loopers.domain.metrics.ProductScoreProjection;
import com.loopers.domain.ranking.ProductRankingWeekly;
import com.loopers.domain.ranking.ProductRankingWeeklyRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 주간 랭킹 집계 Tasklet.
 *
 * <p>JobParameters의 {@code date}(yyyyMMdd)를 기준으로
 * 직전 7일(당일 제외)의 {@code product_metrics}를 집계하여 상품별 가중 스코어 상위 100개를 {@code mv_product_rank_weekly}에 upsert한다.</p>
 *
 * <p>스코어 공식: {@code view_count × 0.1 + like_count × 0.2 + order_count × 0.7}</p>
 *
 * <p>집계 범위 예시 (date=20260413):</p>
 * <pre>
 * start: 2026-04-06
 * end:   2026-04-12
 * </pre>
 */
@Slf4j
@StepScope
@ConditionalOnProperty(name = "spring.batch.job.name", havingValue = WeeklyRankingJobConfig.JOB_NAME)
@RequiredArgsConstructor
@Component
public class WeeklyRankingTasklet implements Tasklet {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final int LIMIT = 100;

    private final ProductMetricsRepository productMetricsRepository;
    private final ProductRankingWeeklyRepository productRankingWeeklyRepository;

    @Value("#{jobParameters['date']}")
    private String date;

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        if (Objects.isNull(date)) {
            throw new IllegalArgumentException("JobParameter 'date' is required");
        }

        LocalDate baseDate;
        try {
            baseDate = LocalDate.parse(date, DATE_FORMAT);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("JobParameter 'date' must be yyyyMMdd format: " + date, e);
        }
        LocalDate start = baseDate.minusDays(7);
        LocalDate end = baseDate.minusDays(1);

        log.info("주간 랭킹 집계 시작 — 기준일: {}, 범위: {} ~ {}", baseDate, start, end);

        List<ProductScoreProjection> topScores = productMetricsRepository.findTopScores(start, end, LIMIT);
        log.info("Top {} 스코어 집계 완료 — {}건", LIMIT, topScores.size());

        List<ProductRankingWeekly> rankings = topScores.stream()
                .map(p -> ProductRankingWeekly.create(p.productId(), baseDate, p.score()))
                .toList();

        productRankingWeeklyRepository.saveAll(rankings);
        log.info("주간 랭킹 집계 완료 — {}개 상품 저장", rankings.size());

        return RepeatStatus.FINISHED;
    }
}
