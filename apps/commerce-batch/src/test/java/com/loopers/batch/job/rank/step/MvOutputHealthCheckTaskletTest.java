package com.loopers.batch.job.rank.step;

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

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@TestPropertySource(properties = "spring.batch.job.enabled=false")
@DisplayName("MV 출력 헬스체크 Tasklet")
class MvOutputHealthCheckTaskletTest {

    @Autowired MvProductRankRepository repository;
    @Autowired JdbcTemplate jdbc;
    @Autowired DatabaseCleanUp databaseCleanUp;

    @BeforeEach
    void setUp() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("row 수가 min 미만이면 FAILED 전파")
    @Test
    void rowsBelowMin_throws() {
        repository.batchInsert(RankPeriodType.WEEKLY, buildRows("2026W15", 5));
        MvOutputHealthCheckTasklet tasklet = new MvOutputHealthCheckTasklet(
                jdbc, RankPeriodType.WEEKLY, "2026W15", null, 10L, 0.5, true
        );
        assertThatThrownBy(() -> tasklet.execute(null, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("MV row 부족");
    }

    @DisplayName("전주 대비 변동폭이 임계 초과 시 FAILED 전파")
    @Test
    void varianceExceedsThreshold_throws() {
        repository.batchInsert(RankPeriodType.WEEKLY, buildRows("2026W14", 100));
        repository.batchInsert(RankPeriodType.WEEKLY, buildRows("2026W15", 20));
        MvOutputHealthCheckTasklet tasklet = new MvOutputHealthCheckTasklet(
                jdbc, RankPeriodType.WEEKLY, "2026W15", "2026W14", 10L, 0.5, true
        );
        assertThatThrownBy(() -> tasklet.execute(null, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("변동폭 초과");
    }

    @DisplayName("정상 범위에서 통과")
    @Test
    void normalRange_passes() {
        repository.batchInsert(RankPeriodType.WEEKLY, buildRows("2026W14", 100));
        repository.batchInsert(RankPeriodType.WEEKLY, buildRows("2026W15", 95));
        MvOutputHealthCheckTasklet tasklet = new MvOutputHealthCheckTasklet(
                jdbc, RankPeriodType.WEEKLY, "2026W15", "2026W14", 10L, 0.5, true
        );
        assertThatCode(() -> tasklet.execute(null, null)).doesNotThrowAnyException();
    }

    @DisplayName("failOnAnomaly=false면 예외 없이 경고 로그")
    @Test
    void failFalse_warnsOnly() {
        repository.batchInsert(RankPeriodType.WEEKLY, buildRows("2026W15", 5));
        MvOutputHealthCheckTasklet tasklet = new MvOutputHealthCheckTasklet(
                jdbc, RankPeriodType.WEEKLY, "2026W15", null, 10L, 0.5, false
        );
        assertThatCode(() -> tasklet.execute(null, null)).doesNotThrowAnyException();
    }

    private List<MvProductRankRow> buildRows(String periodKey, int count) {
        List<MvProductRankRow> rows = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            rows.add(new MvProductRankRow(
                    periodKey, i + 1, (long) (i + 1), (double) (count - i),
                    10L, 5L, BigDecimal.valueOf(100)
            ));
        }
        return rows;
    }
}
