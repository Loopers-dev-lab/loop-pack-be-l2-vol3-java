package com.loopers.batch.job.ranking.step;

import com.loopers.batch.job.ranking.RankingWeeklyJobConfig;
import com.loopers.infrastructure.ranking.MvProductRankWeeklyEntity;
import com.loopers.infrastructure.ranking.MvProductRankWeeklyJpaRepository;
import java.time.DayOfWeek;
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
@ConditionalOnProperty(name = "spring.batch.job.name", havingValue = RankingWeeklyJobConfig.JOB_NAME)
@RequiredArgsConstructor
@Component
public class WeeklyRankAssignTasklet implements Tasklet {

    private static final int TOP_RANK_LIMIT = 100;

    private final MvProductRankWeeklyJpaRepository weeklyRepository;

    @Value("#{jobParameters['targetDate']}")
    private String targetDate;

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        String yearWeek = LocalDate.parse(targetDate, DateTimeFormatter.BASIC_ISO_DATE)
                .with(DayOfWeek.MONDAY)
                .format(DateTimeFormatter.BASIC_ISO_DATE);

        weeklyRepository.deleteAllByYearWeekNot(yearWeek);

        List<MvProductRankWeeklyEntity> ranked = weeklyRepository.findAllByYearWeekOrderByScoreDesc(yearWeek);

        for (int i = 0; i < Math.min(ranked.size(), TOP_RANK_LIMIT); i++) {
            ranked.get(i).update(ranked.get(i).getScore(), yearWeek, i + 1);
        }

        if (ranked.size() > TOP_RANK_LIMIT) {
            weeklyRepository.deleteAll(ranked.subList(TOP_RANK_LIMIT, ranked.size()));
            log.info("주간 랭킹 {}위 이후 {}건 삭제", TOP_RANK_LIMIT, ranked.size() - TOP_RANK_LIMIT);
        }

        log.info("주간 랭킹 확정: {}건 (yearWeek={})", Math.min(ranked.size(), TOP_RANK_LIMIT), yearWeek);
        return RepeatStatus.FINISHED;
    }
}
