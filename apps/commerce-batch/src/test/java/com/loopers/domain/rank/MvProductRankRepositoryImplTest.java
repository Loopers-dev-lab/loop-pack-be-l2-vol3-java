package com.loopers.domain.rank;

import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@TestPropertySource(properties = "spring.batch.job.enabled=false")
class MvProductRankRepositoryImplTest {

    @Autowired
    private MvProductRankRepository repository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @BeforeEach
    void setUp() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("batchInsert로 주간 랭킹을 적재하고 findByPeriodKey로 조회할 수 있다")
    @Test
    void batchInsert_and_findByPeriodKey() {
        // arrange
        String periodKey = "2026W15";
        List<MvProductRankRow> rows = List.of(
                new MvProductRankRow(periodKey, 1, 42L, 5040.0, 700, 350, BigDecimal.valueOf(7000)),
                new MvProductRankRow(periodKey, 2, 43L, 3600.0, 500, 250, BigDecimal.valueOf(5000)),
                new MvProductRankRow(periodKey, 3, 44L, 1800.0, 250, 125, BigDecimal.valueOf(2500))
        );

        // act
        repository.batchInsert(RankPeriodType.WEEKLY, rows);

        // assert
        List<MvProductRankRow> result = repository.findByPeriodKey(RankPeriodType.WEEKLY, periodKey, 0, 10);
        assertThat(result).hasSize(3);
        assertThat(result.get(0).rankNo()).isEqualTo(1);
        assertThat(result.get(0).refProductId()).isEqualTo(42L);
        assertThat(result.get(0).score()).isEqualTo(5040.0);
    }

    @DisplayName("deleteByPeriodKey로 해당 기간의 랭킹만 삭제한다")
    @Test
    void deleteByPeriodKey_deletesOnlyTargetPeriod() {
        // arrange
        List<MvProductRankRow> week15 = List.of(
                new MvProductRankRow("2026W15", 1, 42L, 5040.0, 700, 350, BigDecimal.valueOf(7000))
        );
        List<MvProductRankRow> week16 = List.of(
                new MvProductRankRow("2026W16", 1, 43L, 3600.0, 500, 250, BigDecimal.valueOf(5000))
        );
        repository.batchInsert(RankPeriodType.WEEKLY, week15);
        repository.batchInsert(RankPeriodType.WEEKLY, week16);

        // act
        repository.deleteByPeriodKey(RankPeriodType.WEEKLY, "2026W15");

        // assert
        assertThat(repository.countByPeriodKey(RankPeriodType.WEEKLY, "2026W15")).isZero();
        assertThat(repository.countByPeriodKey(RankPeriodType.WEEKLY, "2026W16")).isEqualTo(1);
    }

    @DisplayName("findByPeriodKey는 rank_no 순으로 정렬되어 반환한다")
    @Test
    void findByPeriodKey_orderedByRankNo() {
        // arrange
        String periodKey = "2026W15";
        List<MvProductRankRow> rows = List.of(
                new MvProductRankRow(periodKey, 3, 44L, 1800.0, 250, 125, BigDecimal.valueOf(2500)),
                new MvProductRankRow(periodKey, 1, 42L, 5040.0, 700, 350, BigDecimal.valueOf(7000)),
                new MvProductRankRow(periodKey, 2, 43L, 3600.0, 500, 250, BigDecimal.valueOf(5000))
        );
        repository.batchInsert(RankPeriodType.WEEKLY, rows);

        // act
        List<MvProductRankRow> result = repository.findByPeriodKey(RankPeriodType.WEEKLY, periodKey, 0, 10);

        // assert
        assertThat(result).extracting(MvProductRankRow::rankNo).containsExactly(1, 2, 3);
    }

    @DisplayName("findByPeriodKey 페이징이 정상 동작한다")
    @Test
    void findByPeriodKey_pagination() {
        // arrange
        String periodKey = "2026W15";
        List<MvProductRankRow> rows = List.of(
                new MvProductRankRow(periodKey, 1, 42L, 5040.0, 700, 350, BigDecimal.valueOf(7000)),
                new MvProductRankRow(periodKey, 2, 43L, 3600.0, 500, 250, BigDecimal.valueOf(5000)),
                new MvProductRankRow(periodKey, 3, 44L, 1800.0, 250, 125, BigDecimal.valueOf(2500))
        );
        repository.batchInsert(RankPeriodType.WEEKLY, rows);

        // act
        List<MvProductRankRow> page0 = repository.findByPeriodKey(RankPeriodType.WEEKLY, periodKey, 0, 2);
        List<MvProductRankRow> page1 = repository.findByPeriodKey(RankPeriodType.WEEKLY, periodKey, 2, 2);

        // assert
        assertThat(page0).hasSize(2);
        assertThat(page0).extracting(MvProductRankRow::rankNo).containsExactly(1, 2);
        assertThat(page1).hasSize(1);
        assertThat(page1).extracting(MvProductRankRow::rankNo).containsExactly(3);
    }

    @DisplayName("월간 랭킹도 동일하게 동작한다")
    @Test
    void monthlyRank_works() {
        // arrange
        String periodKey = "202604";
        List<MvProductRankRow> rows = List.of(
                new MvProductRankRow(periodKey, 1, 42L, 15000.0, 2100, 1050, BigDecimal.valueOf(21000))
        );

        // act
        repository.batchInsert(RankPeriodType.MONTHLY, rows);

        // assert
        List<MvProductRankRow> result = repository.findByPeriodKey(RankPeriodType.MONTHLY, periodKey, 0, 10);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).refProductId()).isEqualTo(42L);

        long count = repository.countByPeriodKey(RankPeriodType.MONTHLY, periodKey);
        assertThat(count).isEqualTo(1);
    }
}
