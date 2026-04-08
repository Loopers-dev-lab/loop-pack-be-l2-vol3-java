package com.loopers.infrastructure.ranking.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.support.TransactionTemplate;

import com.loopers.domain.ranking.RankingScore;
import com.loopers.domain.ranking.RankingSnapshot;
import com.loopers.utils.DatabaseCleanUp;

@SpringBootTest
class RankingSnapshotRepositoryImplTest {

    @Autowired
    private RankingSnapshotRepositoryImpl rankingSnapshotRepository;

    @Autowired
    private RankingSnapshotJpaRepository rankingSnapshotJpaRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    private static final LocalDate TODAY = LocalDate.now();

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private void saveAll(LocalDate scoreDate, List<RankingScore> scores) {
        transactionTemplate.executeWithoutResult(status ->
                rankingSnapshotRepository.saveAll(scoreDate, scores)
        );
    }

    @DisplayName("스냅샷을 일괄 저장할 때,")
    @Nested
    class SaveAll {

        @DisplayName("스냅샷이 DB에 저장된다.")
        @Test
        void savesSnapshotsToDB() {
            // arrange
            List<RankingScore> scores = List.of(
                    new RankingScore(1L, 45.3),
                    new RankingScore(2L, 30.1)
            );

            // act
            saveAll(TODAY, scores);

            // assert
            List<RankingSnapshot> results = rankingSnapshotJpaRepository.findAll();
            assertAll(
                    () -> assertThat(results).hasSize(2),
                    () -> assertThat(results).extracting(RankingSnapshot::getProductId)
                            .containsExactlyInAnyOrder(1L, 2L)
            );
        }

        @DisplayName("동일 (productId, scoreDate) 조합이면, score가 갱신된다.")
        @Test
        void updatesScore_whenSameProductAndDate() {
            // arrange
            saveAll(TODAY, List.of(new RankingScore(1L, 10.0)));

            // act
            saveAll(TODAY, List.of(new RankingScore(1L, 50.0)));

            // assert
            List<RankingSnapshot> results = rankingSnapshotJpaRepository.findAll();
            assertAll(
                    () -> assertThat(results).hasSize(1),
                    () -> assertThat(results.get(0).getScore()).isEqualTo(50.0)
            );
        }

        @DisplayName("빈 리스트가 전달되면, 예외 없이 정상 처리된다.")
        @Test
        void handlesEmptyList() {
            // act & assert
            saveAll(TODAY, Collections.emptyList());

            List<RankingSnapshot> results = rankingSnapshotJpaRepository.findAll();
            assertThat(results).isEmpty();
        }
    }
}
