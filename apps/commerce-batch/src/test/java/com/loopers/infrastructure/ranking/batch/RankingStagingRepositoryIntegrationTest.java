package com.loopers.infrastructure.ranking.batch;

import com.loopers.domain.ranking.batch.RankingStagingRankRow;
import com.loopers.domain.ranking.batch.RankingStagingRepository;
import com.loopers.testcontainers.MySqlTestContainersConfig;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.batch.job.enabled=false",
        "outbox.relay.enabled=false"
})
@Import(MySqlTestContainersConfig.class)
class RankingStagingRepositoryIntegrationTest {

    @Autowired
    private RankingStagingRepository rankingStagingRepository;

    @Autowired
    private MvProductRankStagingJpaRepository stagingJpaRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @Test
    @DisplayName("삭제 후 순위 행을 저장하면 조회 시 rank 오름차순이다.")
    void saveRankedRows_afterDelete_returnsOrdered() {
        rankingStagingRepository.deleteByPeriodTypeAndPeriodKey("WEEKLY", "2026W15");
        rankingStagingRepository.saveRankedRows(
                "WEEKLY",
                "2026W15",
                List.of(
                        new RankingStagingRankRow(1, 10L, new BigDecimal("9.00")),
                        new RankingStagingRankRow(2, 20L, new BigDecimal("1.00"))
                )
        );

        List<MvProductRankStagingEntity> rows =
                stagingJpaRepository.findByPeriodTypeAndPeriodKeyOrderByRankValueAsc("WEEKLY", "2026W15");

        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).getRankValue()).isEqualTo(1);
        assertThat(rows.get(0).getProductId()).isEqualTo(10L);
        assertThat(rows.get(1).getRankValue()).isEqualTo(2);
    }
}
