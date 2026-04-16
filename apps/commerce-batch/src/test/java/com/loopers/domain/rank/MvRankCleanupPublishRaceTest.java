package com.loopers.domain.rank;

import com.loopers.batch.job.rank.step.MvRankCleanupTasklet;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@TestPropertySource(properties = "spring.batch.job.enabled=false")
@DisplayName("Cleanup ↔ Publish race — 교차 실행 시 published 행 보존 + orphan 정리")
class MvRankCleanupPublishRaceTest {

    private static final String PERIOD_KEY = "2026W15";

    @Autowired MvProductRankRepository rankRepository;
    @Autowired MvProductRankPublicationRepository publicationRepository;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired DatabaseCleanUp databaseCleanUp;

    @BeforeEach
    void setUp() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("Cleanup 진행 중 새 Publish 발생 → 새 published version 보호, 구 orphan 제거")
    @Test
    void concurrentCleanupAndPublish_preservesPublished() throws Exception {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        long v1 = publicationRepository.bumpNextVersion(RankPeriodType.WEEKLY, PERIOD_KEY);
        rankRepository.batchInsert(RankPeriodType.WEEKLY, buildRows(50, 1_000L, v1));
        publicationRepository.casPublishIfGreater(RankPeriodType.WEEKLY, PERIOD_KEY, v1);

        long v2 = publicationRepository.bumpNextVersion(RankPeriodType.WEEKLY, PERIOD_KEY);
        rankRepository.batchInsert(RankPeriodType.WEEKLY, buildRows(50, 2_000L, v2));
        publicationRepository.casPublishIfGreater(RankPeriodType.WEEKLY, PERIOD_KEY, v2);

        CountDownLatch readyLatch = new CountDownLatch(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        Runnable cleanupTask = () -> {
            readyLatch.countDown();
            await(startLatch);
            MvRankCleanupTasklet tasklet = new MvRankCleanupTasklet(
                    jdbcTemplate, RankPeriodType.WEEKLY, PERIOD_KEY, 10
            );
            tasklet.execute(null, null);
        };
        Runnable publishTask = () -> {
            readyLatch.countDown();
            await(startLatch);
            tx.executeWithoutResult(status -> {
                long v3 = publicationRepository.bumpNextVersion(RankPeriodType.WEEKLY, PERIOD_KEY);
                rankRepository.batchInsert(RankPeriodType.WEEKLY, buildRows(50, 3_000L, v3));
                publicationRepository.casPublishIfGreater(RankPeriodType.WEEKLY, PERIOD_KEY, v3);
            });
        };

        executor.submit(cleanupTask);
        executor.submit(publishTask);
        readyLatch.await();
        startLatch.countDown();
        executor.shutdown();
        executor.awaitTermination(30, TimeUnit.SECONDS);

        long published = publicationRepository.findPublishedVersion(RankPeriodType.WEEKLY, PERIOD_KEY);
        assertThat(published).as("published는 단조 증가 — v2 또는 v3").isIn(v2, 3L);

        Integer publishedRows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM mv_product_rank_weekly WHERE period_key=? AND version=?",
                Integer.class, PERIOD_KEY, published
        );
        assertThat(publishedRows).as("현재 published version 행은 온전 — cleanup이 지우지 않음").isEqualTo(50);

        MvRankCleanupTasklet finalCleanup = new MvRankCleanupTasklet(
                jdbcTemplate, RankPeriodType.WEEKLY, PERIOD_KEY, 1000
        );
        finalCleanup.execute(null, null);

        Integer remaining = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM mv_product_rank_weekly WHERE period_key=?",
                Integer.class, PERIOD_KEY
        );
        assertThat(remaining).as("최종 cleanup 후 published version 행만 50개 남음").isEqualTo(50);
    }

    private List<MvProductRankRow> buildRows(int count, long refIdBase, long version) {
        List<MvProductRankRow> rows = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            rows.add(new MvProductRankRow(
                    PERIOD_KEY, i + 1, refIdBase + i,
                    (double) (count - i), 10L, 5L, BigDecimal.valueOf(100), version
            ));
        }
        return rows;
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
