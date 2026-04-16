package com.loopers.domain.rank;

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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@TestPropertySource(properties = "spring.batch.job.enabled=false")
@DisplayName("S2 동시 writer — bump/INSERT/CAS 흐름에서 둘 다 성공, 최종 published는 1건")
class MvPublishingConcurrentWriterTest {

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

    @DisplayName("동일 periodKey로 두 writer 동시 실행 — 둘 다 성공, 최종 published version은 후행 writer")
    @Test
    void bothSucceed_publishedIsLatest() throws Exception {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);

        int rowsA = 50;
        int rowsB = 100;
        long baseA = 1L;
        long baseB = 1_000L;

        ExecutorService executor = Executors.newFixedThreadPool(2);
        Runnable writerA = () -> runWriter(tx, rowsA, baseA);
        Runnable writerB = () -> runWriter(tx, rowsB, baseB);

        Future<?> fa = executor.submit(writerA);
        Future<?> fb = executor.submit(writerB);

        fa.get(15, TimeUnit.SECONDS);
        fb.get(15, TimeUnit.SECONDS);
        executor.shutdown();

        long published = publicationRepository.findPublishedVersion(RankPeriodType.WEEKLY, PERIOD_KEY);
        assertThat(published).as("둘 중 하나의 version이 published").isIn(1L, 2L);

        Integer publishedRowCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM mv_product_rank_weekly WHERE period_key=? AND version=?",
                Integer.class, PERIOD_KEY, published
        );
        assertThat(publishedRowCount).as("published version 행수는 그 writer가 insert한 만큼").isIn(rowsA, rowsB);

        Integer totalRows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM mv_product_rank_weekly WHERE period_key=?",
                Integer.class, PERIOD_KEY
        );
        assertThat(totalRows).as("두 version 모두 테이블에 공존 (cleanup 별도)").isEqualTo(rowsA + rowsB);
    }

    private void runWriter(TransactionTemplate tx, int count, long idBase) {
        Long myVersion = tx.execute(status -> {
            long v = publicationRepository.bumpNextVersion(RankPeriodType.WEEKLY, PERIOD_KEY);
            rankRepository.batchInsert(RankPeriodType.WEEKLY, buildRows(count, idBase, v));
            return v;
        });
        if (myVersion != null) {
            publicationRepository.casPublishIfGreater(RankPeriodType.WEEKLY, PERIOD_KEY, myVersion);
        }
    }

    private List<MvProductRankRow> buildRows(int count, long refIdBase, long version) {
        List<MvProductRankRow> rows = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            rows.add(new MvProductRankRow(
                    PERIOD_KEY, i + 1, refIdBase + i, (double) (count - i),
                    10L, 5L, BigDecimal.valueOf(100), version
            ));
        }
        return rows;
    }
}
