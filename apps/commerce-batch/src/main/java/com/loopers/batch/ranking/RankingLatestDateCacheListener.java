package com.loopers.batch.ranking;

import com.loopers.domain.rank.MvProductRankMonthlyRepository;
import com.loopers.domain.rank.MvProductRankWeeklyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;

@Slf4j
@Component
@RequiredArgsConstructor
public class RankingLatestDateCacheListener implements JobExecutionListener {

    private final StringRedisTemplate stringRedisTemplate;
    private final MvProductRankWeeklyRepository weeklyRepository;
    private final MvProductRankMonthlyRepository monthlyRepository;

    private static final Duration TTL = Duration.ofHours(25);

    @Override
    public void afterJob(JobExecution jobExecution) {
        if (jobExecution.getStatus() != BatchStatus.COMPLETED) {
            return;
        }
        String jobName = jobExecution.getJobInstance().getJobName();
        String snapshotDateStr = jobExecution.getJobParameters().getString("snapshotDate");
        if (snapshotDateStr == null) return;

        String cacheKey = resolveCacheKey(jobName);
        if (cacheKey == null) return;

        LocalDate snapshotDate = LocalDate.parse(snapshotDateStr);
        long count = countByJobName(jobName, snapshotDate);
        if (count == 0) {
            log.warn("[{}] snapshotDate={} 적재 데이터 없음 — latest_date 캐시 갱신 스킵", jobName, snapshotDate);
            return;
        }

        stringRedisTemplate.opsForValue().set(cacheKey, snapshotDateStr, TTL);
        log.info("[{}] latest_date 캐시 put: {} -> {}", jobName, cacheKey, snapshotDateStr);
    }

    private long countByJobName(String jobName, LocalDate snapshotDate) {
        return switch (jobName) {
            case "weeklyRankingJob"  -> weeklyRepository.countBySnapshotDate(snapshotDate);
            case "monthlyRankingJob" -> monthlyRepository.countBySnapshotDate(snapshotDate);
            default -> 0L;
        };
    }

    private String resolveCacheKey(String jobName) {
        return switch (jobName) {
            case "weeklyRankingJob"  -> "rankings:weekly:latest_date";
            case "monthlyRankingJob" -> "rankings:monthly:latest_date";
            default -> null;
        };
    }
}
