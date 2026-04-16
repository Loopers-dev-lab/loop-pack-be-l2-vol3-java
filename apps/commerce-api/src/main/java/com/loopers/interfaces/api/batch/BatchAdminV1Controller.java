package com.loopers.interfaces.api.batch;

import com.loopers.batch.job.common.RankingJobConstants;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/**
 * Batch Job 실행 Admin API Controller.
 */
@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/api-admin/v1/batch")
public class BatchAdminV1Controller implements BatchAdminV1ApiSpec {

    private final JobLauncher jobLauncher;

    @Qualifier("weeklyRankingJob")
    private final Job weeklyRankingJob;

    @Qualifier("monthlyRankingJob")
    private final Job monthlyRankingJob;

    @PostMapping("/weekly-ranking")
    @ResponseStatus(HttpStatus.OK)
    @Override
    public ApiResponse<BatchAdminV1Dto.JobExecutionResponse> runWeeklyRankingJob(
        @RequestParam String targetDate
    ) {
        validateTargetDateFormat(targetDate);
        JobExecution execution = launchJob(weeklyRankingJob, targetDate);
        return ApiResponse.success(BatchAdminV1Dto.JobExecutionResponse.from(execution));
    }

    @PostMapping("/monthly-ranking")
    @ResponseStatus(HttpStatus.OK)
    @Override
    public ApiResponse<BatchAdminV1Dto.JobExecutionResponse> runMonthlyRankingJob(
        @RequestParam String targetDate
    ) {
        validateTargetDateFormat(targetDate);
        JobExecution execution = launchJob(monthlyRankingJob, targetDate);
        return ApiResponse.success(BatchAdminV1Dto.JobExecutionResponse.from(execution));
    }

    private void validateTargetDateFormat(String targetDate) {
        try {
            LocalDate.parse(targetDate, RankingJobConstants.DATE_FORMATTER);
        } catch (DateTimeParseException e) {
            throw new CoreException(
                ErrorType.BATCH_INVALID_DATE_FORMAT,
                "잘못된 날짜 형식입니다. 예상: yyyyMMdd, 입력값: " + targetDate
            );
        }
    }

    private JobExecution launchJob(Job job, String targetDate) {
        try {
            JobParameters params = new JobParametersBuilder()
                .addString("targetDate", targetDate)
                .addLong("runId", System.currentTimeMillis())
                .toJobParameters();

            log.info("Launching job: {}, targetDate: {}", job.getName(), targetDate);
            JobExecution execution = jobLauncher.run(job, params);
            log.info("Job completed: {}, status: {}", job.getName(), execution.getStatus());

            return execution;
        } catch (Exception e) {
            log.error("Failed to launch job: {}", job.getName(), e);
            throw new CoreException(
                ErrorType.BATCH_JOB_FAILED,
                "배치 Job 실행 실패: " + e.getMessage()
            );
        }
    }
}