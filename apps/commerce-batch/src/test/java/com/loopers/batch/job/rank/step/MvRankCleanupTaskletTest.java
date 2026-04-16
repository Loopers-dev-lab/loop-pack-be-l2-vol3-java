package com.loopers.batch.job.rank.step;

import com.loopers.domain.rank.MvProductRankPublicationRepository;
import com.loopers.domain.rank.MvProductRankRepository;
import com.loopers.domain.rank.MvProductRankRow;
import com.loopers.domain.rank.RankPeriodType;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@TestPropertySource(properties = "spring.batch.job.enabled=false")
@DisplayName("MV Cleanup Tasklet — version < published_version 고아 삭제")
class MvRankCleanupTaskletTest {

    private static final String KEY = "2026W15";

    @Autowired MvProductRankRepository rankRepository;
    @Autowired MvProductRankPublicationRepository publicationRepository;
    @Autowired JdbcTemplate jdbc;
    @Autowired DatabaseCleanUp databaseCleanUp;

    @BeforeEach
    void setUp() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("published_version 미만 행만 삭제, published_version 유지")
    @Test
    void deletesOrphansOnly() {
        rankRepository.batchInsert(RankPeriodType.WEEKLY, buildRows(10, 1L));
        rankRepository.batchInsert(RankPeriodType.WEEKLY, buildRows(10, 2L));
        rankRepository.batchInsert(RankPeriodType.WEEKLY, buildRows(10, 3L));
        seedPublication(3L, 3L);

        MvRankCleanupTasklet tasklet = new MvRankCleanupTasklet(
                jdbc, RankPeriodType.WEEKLY, KEY, 1000
        );
        tasklet.execute(null, null);

        Integer remainingRows = jdbc.queryForObject(
                "SELECT COUNT(*) FROM mv_product_rank_weekly WHERE period_key=?",
                Integer.class, KEY
        );
        assertThat(remainingRows).as("published version(3)만 남음").isEqualTo(10);

        Integer orphanRows = jdbc.queryForObject(
                "SELECT COUNT(*) FROM mv_product_rank_weekly WHERE period_key=? AND version < 3",
                Integer.class, KEY
        );
        assertThat(orphanRows).isZero();
    }

    @DisplayName("published_version 부재 시 no-op")
    @Test
    void noPublishedVersion_noop() {
        rankRepository.batchInsert(RankPeriodType.WEEKLY, buildRows(5, 1L));

        MvRankCleanupTasklet tasklet = new MvRankCleanupTasklet(
                jdbc, RankPeriodType.WEEKLY, KEY, 1000
        );
        tasklet.execute(null, null);

        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM mv_product_rank_weekly WHERE period_key=?",
                Integer.class, KEY
        );
        assertThat(count).isEqualTo(5);
    }

    @DisplayName("batchLimit보다 많은 orphan — 반복 삭제로 전량 제거")
    @Test
    void manyOrphans_repeatedDeletionClearsAll() {
        rankRepository.batchInsert(RankPeriodType.WEEKLY, buildRows(100, 1L));
        rankRepository.batchInsert(RankPeriodType.WEEKLY, buildRows(100, 2L));
        seedPublication(2L, 2L);

        MvRankCleanupTasklet tasklet = new MvRankCleanupTasklet(
                jdbc, RankPeriodType.WEEKLY, KEY, 30
        );
        tasklet.execute(null, null);

        Integer orphanRows = jdbc.queryForObject(
                "SELECT COUNT(*) FROM mv_product_rank_weekly WHERE period_key=? AND version < 2",
                Integer.class, KEY
        );
        assertThat(orphanRows).isZero();
        Integer remainingRows = jdbc.queryForObject(
                "SELECT COUNT(*) FROM mv_product_rank_weekly WHERE period_key=?",
                Integer.class, KEY
        );
        assertThat(remainingRows).isEqualTo(100);
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

    private void seedPublication(long nextV, long publishedV) {
        jdbc.update("INSERT INTO mv_product_rank_publication " +
                "(period_type, period_key, next_version, published_version, updated_at) " +
                "VALUES (?, ?, ?, ?, NOW(6))", "WEEKLY", KEY, nextV, publishedV);
    }
}
