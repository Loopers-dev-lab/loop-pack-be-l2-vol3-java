package com.loopers.batch.ranking;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * CLI 수동 재실행 진입점.
 *
 * --trigger 옵션이 없으면 아무 동작도 하지 않으며 스케줄러 모드로 동작한다.
 * 사용 예시:
 *   --spring.batch.job.name=weeklyRankingJob --trigger=WEIGHT_CHANGE
 *   --spring.batch.job.name=weeklyRankingJob --trigger=WEIGHT_CHANGE --snapshotDate=2026-04-10
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class RankingManualRunner implements ApplicationRunner {

    private final JobLauncher jobLauncher;
    private final ApplicationContext applicationContext;

    @Value("${spring.batch.job.name:}")
    private String jobName;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (!args.containsOption("trigger")) {
            return;
        }

        String triggerStr = args.getOptionValues("trigger").get(0);
        RankingJobTrigger trigger = RankingJobTrigger.valueOf(triggerStr);

        if (jobName.isBlank()) {
            log.warn("[RankingManualRunner] spring.batch.job.name 미지정 — 수동 실행 불가");
            return;
        }

        LocalDate snapshotDate = args.containsOption("snapshotDate")
            ? LocalDate.parse(args.getOptionValues("snapshotDate").get(0))
            : LocalDate.now(ZoneId.of("Asia/Seoul"));

        Job job = applicationContext.getBean(jobName, Job.class);
        log.info("[RankingManualRunner] 수동 실행: job={}, snapshotDate={}, trigger={}", jobName, snapshotDate, trigger);

        jobLauncher.run(job, new JobParametersBuilder()
            .addString("snapshotDate", snapshotDate.toString())
            .addString("trigger", trigger.name())
            .toJobParameters());
    }
}
