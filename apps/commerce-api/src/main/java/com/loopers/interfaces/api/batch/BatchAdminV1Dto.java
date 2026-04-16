package com.loopers.interfaces.api.batch;

import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;

import java.time.LocalDateTime;

/**
 * Batch Admin API DTO.
 */
public final class BatchAdminV1Dto {

    private BatchAdminV1Dto() {}

    /**
     * Job 실행 결과 응답.
     */
    public record JobExecutionResponse(
        Long executionId,
        String jobName,
        BatchStatus status,
        LocalDateTime startTime,
        LocalDateTime endTime,
        String exitCode,
        String exitDescription
    ) {
        public static JobExecutionResponse from(JobExecution execution) {
            return new JobExecutionResponse(
                execution.getId(),
                execution.getJobInstance().getJobName(),
                execution.getStatus(),
                execution.getStartTime(),
                execution.getEndTime(),
                execution.getExitStatus().getExitCode(),
                execution.getExitStatus().getExitDescription()
            );
        }
    }
}