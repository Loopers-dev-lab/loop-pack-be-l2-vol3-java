package com.loopers.domain.score;

import com.loopers.infrastructure.score.MvProductScoreDailyJpaRepository;
import com.loopers.infrastructure.score.MvProductScoreDailyRepositoryImpl;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@TestPropertySource(properties = "spring.batch.job.enabled=false")
class MvProductScoreDailyRepositoryImplTest {

    @Autowired
    private MvProductScoreDailyRepository repository;

    @Autowired
    private MvProductScoreDailyJpaRepository jpaRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @BeforeEach
    void setUp() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("batchUpsert로 일간 점수를 적재하고 조회할 수 있다")
    @Test
    void batchUpsert_insertsRows() {
        // arrange
        LocalDate date = LocalDate.of(2026, 4, 11);
        List<MvProductScoreDailyRow> rows = List.of(
                new MvProductScoreDailyRow(1L, date, 720.0, 100, 50, BigDecimal.valueOf(1000)),
                new MvProductScoreDailyRow(2L, date, 360.0, 50, 25, BigDecimal.valueOf(500))
        );

        // act
        repository.batchUpsert(rows);

        // assert
        List<MvProductScoreDailyModel> result = jpaRepository.findAll();
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getScore()).isEqualTo(720.0);
        assertThat(result.get(0).getId().getProductDbId()).isEqualTo(1L);
        assertThat(result.get(0).getId().getScoreDate()).isEqualTo(date);
    }

    @DisplayName("동일 PK로 batchUpsert 시 ON DUPLICATE KEY UPDATE로 덮어쓴다")
    @Test
    void batchUpsert_upsertOnDuplicate() {
        // arrange
        LocalDate date = LocalDate.of(2026, 4, 11);
        List<MvProductScoreDailyRow> initial = List.of(
                new MvProductScoreDailyRow(1L, date, 100.0, 10, 5, BigDecimal.valueOf(100))
        );
        repository.batchUpsert(initial);

        List<MvProductScoreDailyRow> updated = List.of(
                new MvProductScoreDailyRow(1L, date, 720.0, 100, 50, BigDecimal.valueOf(1000))
        );

        // act
        repository.batchUpsert(updated);

        // assert
        List<MvProductScoreDailyModel> result = jpaRepository.findAll();
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getScore()).isEqualTo(720.0);
        assertThat(result.get(0).getViewCount()).isEqualTo(100);
    }

    @DisplayName("EmbeddedId의 equals/hashCode가 정상 동작한다")
    @Test
    void embeddedId_equalsAndHashCode() {
        // arrange
        MvProductScoreDailyId id1 = new MvProductScoreDailyId(1L, LocalDate.of(2026, 4, 11));
        MvProductScoreDailyId id2 = new MvProductScoreDailyId(1L, LocalDate.of(2026, 4, 11));
        MvProductScoreDailyId id3 = new MvProductScoreDailyId(2L, LocalDate.of(2026, 4, 11));

        // assert
        assertThat(id1).isEqualTo(id2);
        assertThat(id1).isNotEqualTo(id3);
        assertThat(id1.hashCode()).isEqualTo(id2.hashCode());
    }
}
