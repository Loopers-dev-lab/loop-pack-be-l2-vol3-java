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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@TestPropertySource(properties = "spring.batch.job.enabled=false")
@DisplayName("Publication 원자성 — bump rollback, cleanup 멱등성")
class MvPublicationAtomicityTest {

    private static final String KEY = "2026W15";

    @Autowired MvProductRankRepository rankRepository;
    @Autowired MvProductRankPublicationRepository publicationRepository;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired DatabaseCleanUp databaseCleanUp;

    @BeforeEach
    void setUp() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("bump 후 tx rollback 시 next_version 복원 — publication 원자성")
    @Test
    void bumpRollback_restoresNextVersion() {
        publicationRepository.bumpNextVersion(RankPeriodType.WEEKLY, KEY);
        publicationRepository.bumpNextVersion(RankPeriodType.WEEKLY, KEY);
        Long beforeNext = jdbc.queryForObject(
                "SELECT next_version FROM mv_product_rank_publication WHERE period_type=? AND period_key=?",
                Long.class, "WEEKLY", KEY);
        assertThat(beforeNext).isEqualTo(2L);

        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
            publicationRepository.bumpNextVersion(RankPeriodType.WEEKLY, KEY);
            throw new RuntimeException("simulated insertTx failure");
        })).hasMessageContaining("simulated");

        Long afterNext = jdbc.queryForObject(
                "SELECT next_version FROM mv_product_rank_publication WHERE period_type=? AND period_key=?",
                Long.class, "WEEKLY", KEY);
        assertThat(afterNext).as("rollback 후 next_version 되돌아가야 함").isEqualTo(2L);
    }

    @DisplayName("Cleanup 멱등성 — 같은 periodKey로 2회 실행 시 결과 동일 + 예외 없음")
    @Test
    void cleanupIdempotent() {
        rankRepository.batchInsert(RankPeriodType.WEEKLY, buildRows(10, 1L));
        rankRepository.batchInsert(RankPeriodType.WEEKLY, buildRows(10, 2L));
        jdbc.update("INSERT INTO mv_product_rank_publication (period_type, period_key, next_version, published_version, updated_at) " +
                "VALUES ('WEEKLY', ?, 2, 2, NOW(6))", KEY);

        MvRankCleanupTasklet tasklet = new MvRankCleanupTasklet(
                jdbc, RankPeriodType.WEEKLY, KEY, 1000
        );
        tasklet.execute(null, null);
        tasklet.execute(null, null);
        tasklet.execute(null, null);

        Integer rowCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM mv_product_rank_weekly WHERE period_key=?",
                Integer.class, KEY
        );
        assertThat(rowCount).as("3회 실행해도 published(v2) 행 10개만 유지").isEqualTo(10);
    }

    @DisplayName("CAS publish 단조성 — 동일 version 재시도는 실패, 하위 version 시도도 실패")
    @Test
    void casMonotonic() {
        publicationRepository.bumpNextVersion(RankPeriodType.WEEKLY, KEY);
        publicationRepository.bumpNextVersion(RankPeriodType.WEEKLY, KEY);

        assertThat(publicationRepository.casPublishIfGreater(RankPeriodType.WEEKLY, KEY, 2L)).isTrue();
        assertThat(publicationRepository.casPublishIfGreater(RankPeriodType.WEEKLY, KEY, 2L))
                .as("동일 version 재시도 — 이미 published=2라 false").isFalse();
        assertThat(publicationRepository.casPublishIfGreater(RankPeriodType.WEEKLY, KEY, 1L))
                .as("하위 version 시도 실패").isFalse();
        assertThat(publicationRepository.findPublishedVersion(RankPeriodType.WEEKLY, KEY)).isEqualTo(2L);
    }

    private List<MvProductRankRow> buildRows(int count, long version) {
        List<MvProductRankRow> rows = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            rows.add(new MvProductRankRow(
                    KEY, i + 1, (version * 10_000L) + i + 1,
                    (double) (count - i), 10L, 5L, BigDecimal.valueOf(100), version
            ));
        }
        return rows;
    }
}
