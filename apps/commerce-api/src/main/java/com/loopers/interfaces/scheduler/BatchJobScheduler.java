package com.loopers.interfaces.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Slf4j
@Component
@RequiredArgsConstructor
public class BatchJobScheduler {

    private final JobLauncher jobLauncher;
    private final Job metricsSnapshotJob;
    private final Job rankingAggregationJob;

    @Scheduled(cron = "0 5 0 * * *")
    public void runDailyBatch() {
        String targetDate = LocalDate.now().minusDays(1).format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        try {
            JobParameters params = new JobParametersBuilder()
                    .addString("targetDate", targetDate)
                    .addLong("run.id", System.currentTimeMillis())
                    .toJobParameters();
            jobLauncher.run(metricsSnapshotJob, params);
            jobLauncher.run(rankingAggregationJob, params);
        } catch (Exception e) {
            log.error("배치 실행 실패: targetDate={}", targetDate, e);
        }
    }
}
