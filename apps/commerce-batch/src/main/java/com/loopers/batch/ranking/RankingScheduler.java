package com.loopers.batch.ranking;

import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.repository.JobInstanceAlreadyCompleteException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * 매일 새벽 주간/월간 랭킹 배치를 자동으로 실행하는 스케줄러.
 * 두 Job 빈이 모두 로드된 경우(데몬 모드)에만 활성화된다.
 */
@Slf4j
@Component
@ConditionalOnBean(name = {"weeklyRankingJob", "monthlyRankingJob"})
public class RankingScheduler {

    private final JobLauncher jobLauncher;
    private final Job weeklyRankingJob;
    private final Job monthlyRankingJob;

    public RankingScheduler(
            JobLauncher jobLauncher,
            @Qualifier("weeklyRankingJob") Job weeklyRankingJob,
            @Qualifier("monthlyRankingJob") Job monthlyRankingJob) {
        this.jobLauncher = jobLauncher;
        this.weeklyRankingJob = weeklyRankingJob;
        this.monthlyRankingJob = monthlyRankingJob;
    }

    @Scheduled(cron = "0 0 1 * * *", zone = "Asia/Seoul")
    public void runWeekly() {
        run(weeklyRankingJob, "weeklyRankingJob");
    }

    @Scheduled(cron = "0 30 1 * * *", zone = "Asia/Seoul")
    public void runMonthly() {
        run(monthlyRankingJob, "monthlyRankingJob");
    }

    private void run(Job job, String jobName) {
        LocalDate snapshotDate = LocalDate.now(ZoneId.of("Asia/Seoul"));
        try {
            jobLauncher.run(job, new JobParametersBuilder()
                .addString("snapshotDate", snapshotDate.toString())
                .addString("trigger", RankingJobTrigger.SCHEDULED.name())
                .toJobParameters());
        } catch (JobInstanceAlreadyCompleteException e) {
            log.warn("[{}] snapshotDate={} 이미 완료된 JobInstance — 재실행 스킵", jobName, snapshotDate);
        } catch (Exception e) {
            log.error("[{}] 실행 실패 snapshotDate={}", jobName, snapshotDate, e);
        }
    }
}
