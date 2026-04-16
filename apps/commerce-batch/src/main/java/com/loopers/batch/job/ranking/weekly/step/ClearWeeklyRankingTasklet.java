package com.loopers.batch.job.ranking.weekly.step;

import com.loopers.batch.job.ranking.weekly.WeeklyRankingJobConfig;
import com.loopers.infrastructure.ranking.MvProductRankWeeklyJpaRepository;
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

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.WeekFields;

/**
 * 주간 MV 테이블에서 해당 주차의 기존 데이터를 삭제하는 Tasklet.
 * 배치 재실행 시 중복 데이터 적재를 방지한다.
 * 흐름: Job 파라미터(requestDate) → yearWeek 계산 → DELETE
 */
@Slf4j
@StepScope
@ConditionalOnProperty(name = "spring.batch.job.name", havingValue = WeeklyRankingJobConfig.JOB_NAME)
@RequiredArgsConstructor
@Component
public class ClearWeeklyRankingTasklet implements Tasklet {

    private final MvProductRankWeeklyJpaRepository weeklyJpaRepository;

    // Job 파라미터에서 실행 기준 날짜를 주입받는다
    // @StepScope가 있어야 #{jobParameters[...]} late binding이 동작한다
    @Value("#{jobParameters['requestDate']}")
    private String requestDate;

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        String yearWeek = computeYearWeek(requestDate);
        log.info("주간 랭킹 MV 초기화: yearWeek={}", yearWeek);

        weeklyJpaRepository.deleteByYearWeek(yearWeek);

        return RepeatStatus.FINISHED;
    }

    /**
     * "yyyyMMdd" 형식의 날짜에서 ISO 주차를 계산한다.
     * 예: "20260412" → 2026년 15주차 → "2026W15"
     */
    public static String computeYearWeek(String dateStr) {
        LocalDate date = LocalDate.parse(dateStr, DateTimeFormatter.ofPattern("yyyyMMdd"));
        int year = date.get(WeekFields.ISO.weekBasedYear());
        int week = date.get(WeekFields.ISO.weekOfWeekBasedYear());
        return String.format("%dW%02d", year, week);
    }
}
