package com.loopers.batch.listener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.annotation.AfterJob;
import org.springframework.batch.core.annotation.BeforeJob;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

@Slf4j
@RequiredArgsConstructor
@Component
public class JobListener {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final String START_TIME_KEY = "startTime";

    @BeforeJob
    void beforeJob(JobExecution jobExecution) {
        String jobName = jobExecution.getJobInstance().getJobName();
        log.info("Job '{}' 시작", jobName);
        jobExecution.getExecutionContext().putLong(START_TIME_KEY, System.currentTimeMillis());
    }

    @AfterJob
    void afterJob(JobExecution jobExecution) {
        String jobName = jobExecution.getJobInstance().getJobName();
        var startTime = jobExecution.getExecutionContext().getLong(START_TIME_KEY);
        var endTime = System.currentTimeMillis();

        var startDateTime = Instant.ofEpochMilli(startTime)
            .atZone(KST)
            .toLocalDateTime();
        var endDateTime = Instant.ofEpochMilli(endTime)
            .atZone(KST)
            .toLocalDateTime();

        var duration = Duration.ofMillis(endTime - startTime);
        var hours = duration.toHours();
        var minutes = duration.toMinutes() % 60;
        var seconds = duration.getSeconds() % 60;

        log.info(
            "Job '{}' 종료. batchStatus={}, exitStatus={}, startTime={}, endTime={}, duration={}시간 {}분 {}초",
            jobName,
            jobExecution.getStatus(),
            jobExecution.getExitStatus().getExitCode(),
            startDateTime,
            endDateTime,
            hours, minutes, seconds
        );
    }
}
