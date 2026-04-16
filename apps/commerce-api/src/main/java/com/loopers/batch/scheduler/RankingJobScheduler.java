package com.loopers.batch.scheduler;

import com.loopers.batch.job.common.RankingJobConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * 랭킹 집계 Job 스케줄러.
 *
 * <p>주간 랭킹: 매주 월요일 새벽 2시 실행 (전주 월~일 집계)
 * <p>월간 랭킹: 매월 1일 새벽 3시 실행 (전월 1일~말일 집계)
 */
@Slf4j
@Component
@RequiredArgsConstructor
@EnableConfigurationProperties(BatchSchedulerProperties.class)
public class RankingJobScheduler {

    private final JobLauncher jobLauncher;
    private final BatchSchedulerProperties properties;

    @Qualifier("weeklyRankingJob")
    private final Job weeklyRankingJob;

    @Qualifier("monthlyRankingJob")
    private final Job monthlyRankingJob;

    /**
     * 주간 랭킹 집계 스케줄.
     * 매주 월요일 새벽 2시에 전주(월~일)의 데이터를 집계합니다.
     */
    @Scheduled(cron = "${batch.scheduler.weekly-cron:0 0 2 ? * MON}")
    public void runWeeklyRankingJob() {
        if (!properties.enabled()) {
            log.debug("Batch scheduler is disabled. Skipping weekly ranking job.");
            return;
        }

        LocalDate yesterday = LocalDate.now().minusDays(1);
        String targetDate = yesterday.format(RankingJobConstants.DATE_FORMATTER);

        log.info("Starting scheduled weekly ranking job: targetDate={}", targetDate);
        launchJob(weeklyRankingJob, targetDate);
    }

    /**
     * 월간 랭킹 집계 스케줄.
     * 매월 1일 새벽 3시에 전월(1일~말일)의 데이터를 집계합니다.
     */
    @Scheduled(cron = "${batch.scheduler.monthly-cron:0 0 3 1 * ?}")
    public void runMonthlyRankingJob() {
        if (!properties.enabled()) {
            log.debug("Batch scheduler is disabled. Skipping monthly ranking job.");
            return;
        }

        LocalDate yesterday = LocalDate.now().minusDays(1);
        String targetDate = yesterday.format(RankingJobConstants.DATE_FORMATTER);

        log.info("Starting scheduled monthly ranking job: targetDate={}", targetDate);
        launchJob(monthlyRankingJob, targetDate);
    }

    private void launchJob(Job job, String targetDate) {
        try {
            JobParameters params = new JobParametersBuilder()
                .addString("targetDate", targetDate)
                .addLong("runId", System.currentTimeMillis())
                .toJobParameters();

            var execution = jobLauncher.run(job, params);
            log.info("Scheduled job completed: {}, status: {}", job.getName(), execution.getStatus());
        } catch (Exception e) {
            log.error("Failed to run scheduled job: {}", job.getName(), e);
        }
    }
}