package com.loopers.infrastructure.rank;

import com.loopers.domain.rank.MvProductRankWeekly;
import com.loopers.domain.rank.MvProductRankWeeklyRepository;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class MvProductRankWeeklyRepositoryImplIntegrationTest {

    @Autowired
    private MvProductRankWeeklyRepository repository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @Nested
    @DisplayName("upsertAll()")
    class UpsertAll {

        @Test
        @DisplayName("최초 호출 시 행이 INSERT 된다")
        void insertOnFirstCall() {
            // arrange
            List<MvProductRankWeekly> rows = List.of(
                new MvProductRankWeekly(LocalDate.of(2026, 4, 16), 1L, 1, 100.0, 10L, 5L, new BigDecimal("1000.00")),
                new MvProductRankWeekly(LocalDate.of(2026, 4, 16), 2L, 2, 90.0, 9L, 4L, new BigDecimal("900.00"))
            );

            // act
            repository.upsertAll(rows);

            // assert
            assertThat(repository.countBySnapshotDate(LocalDate.of(2026, 4, 16))).isEqualTo(2);
        }

        @Test
        @DisplayName("같은 (snapshot_date, product_id) 로 재호출 시 값이 UPDATE 된다 (멱등성)")
        void updateOnDuplicateKey() {
            // arrange
            LocalDate date = LocalDate.of(2026, 4, 16);
            repository.upsertAll(List.of(
                new MvProductRankWeekly(date, 1L, 1, 100.0, 10L, 5L, new BigDecimal("1000.00"))
            ));
            MvProductRankWeekly updated = new MvProductRankWeekly(date, 1L, 1, 200.0, 20L, 10L, new BigDecimal("2000.00"));

            // act
            repository.upsertAll(List.of(updated));

            // assert
            MvProductRankWeekly row = repository.findBySnapshotDateAndProductId(date, 1L).orElseThrow();
            assertThat(row.getScore()).isEqualTo(200.0);
            assertThat(row.getViewCount()).isEqualTo(20L);
        }

        @Test
        @DisplayName("created_at 은 최초 INSERT 시각을 유지하고 UPDATE 시 갱신되지 않는다")
        void createdAtIsPreservedOnUpdate() throws InterruptedException {
            // arrange
            LocalDate date = LocalDate.of(2026, 4, 16);
            repository.upsertAll(List.of(
                new MvProductRankWeekly(date, 1L, 1, 100.0, 10L, 5L, new BigDecimal("1000.00"))
            ));
            LocalDateTime firstCreatedAt = repository.findBySnapshotDateAndProductId(date, 1L).orElseThrow().getCreatedAt();
            Thread.sleep(10);

            // act
            repository.upsertAll(List.of(
                new MvProductRankWeekly(date, 1L, 1, 200.0, 20L, 10L, new BigDecimal("2000.00"))
            ));

            // assert
            LocalDateTime afterUpdate = repository.findBySnapshotDateAndProductId(date, 1L).orElseThrow().getCreatedAt();
            assertThat(afterUpdate).isEqualTo(firstCreatedAt);
        }
    }
}
