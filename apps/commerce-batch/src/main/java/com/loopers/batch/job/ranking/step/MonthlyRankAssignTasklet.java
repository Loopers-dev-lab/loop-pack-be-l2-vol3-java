package com.loopers.batch.job.ranking.step;

import com.loopers.batch.job.ranking.RankingMonthlyJobConfig;
import com.loopers.infrastructure.ranking.MvProductRankMonthlyEntity;
import com.loopers.infrastructure.ranking.MvProductRankMonthlyJpaRepository;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Slf4j
@StepScope
@ConditionalOnProperty(name = "spring.batch.job.name", havingValue = RankingMonthlyJobConfig.JOB_NAME)
@RequiredArgsConstructor
@Component
public class MonthlyRankAssignTasklet implements Tasklet {

    private static final int TOP_RANK_LIMIT = 100;

    private final MvProductRankMonthlyJpaRepository monthlyRepository;

    @Value("#{jobParameters['targetDate']}")
    private String targetDate;

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        String yearMonth = LocalDate.parse(targetDate, DateTimeFormatter.BASIC_ISO_DATE)
                .format(RankingMonthlyJobConfig.YEAR_MONTH_FORMAT);

        monthlyRepository.deleteAllByYearMonthNot(yearMonth);

        List<MvProductRankMonthlyEntity> ranked = monthlyRepository.findAllByYearMonthOrderByScoreDesc(yearMonth);

        for (int i = 0; i < Math.min(ranked.size(), TOP_RANK_LIMIT); i++) {
            ranked.get(i).update(ranked.get(i).getScore(), yearMonth, i + 1);
        }

        if (ranked.size() > TOP_RANK_LIMIT) {
            monthlyRepository.deleteAll(ranked.subList(TOP_RANK_LIMIT, ranked.size()));
            log.info("월간 랭킹 {}위 이후 {}건 삭제", TOP_RANK_LIMIT, ranked.size() - TOP_RANK_LIMIT);
        }

        log.info("월간 랭킹 확정: {}건 (yearMonth={})", Math.min(ranked.size(), TOP_RANK_LIMIT), yearMonth);
        return RepeatStatus.FINISHED;
    }
}
