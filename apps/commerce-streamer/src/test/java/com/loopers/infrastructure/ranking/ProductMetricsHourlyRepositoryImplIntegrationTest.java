package com.loopers.infrastructure.ranking;

import com.loopers.domain.ranking.ProductDailyAggregate;
import com.loopers.domain.ranking.ProductMetricsHourlyRepository;
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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testcontainers 기반 통합 테스트 — Native UPSERT / snapshotByDate 검증.
 */
@SpringBootTest
@DisplayName("ProductMetricsHourlyRepositoryImpl 통합 테스트")
class ProductMetricsHourlyRepositoryImplIntegrationTest {

    @Autowired
    private ProductMetricsHourlyRepository repository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @Nested
    @DisplayName("upsertIncrements — INSERT 분기")
    class Insert {

        @Test
        @DisplayName("최초 호출 시 새 row 가 생성되고 델타가 그대로 반영된다")
        void firstInsert() {
            // given
            LocalDateTime bucket = LocalDateTime.of(2026, 4, 9, 14, 0);

            // when
            repository.upsertIncrements(1L, bucket, 5, 2, 1, BigDecimal.valueOf(10000));

            // then
            ProductDailyAggregate snap = repository.snapshotByDate(1L, bucket.toLocalDate());
            assertThat(snap.totalView()).isEqualTo(5);
            assertThat(snap.totalLike()).isEqualTo(2);
            assertThat(snap.totalOrder()).isEqualTo(1);
            assertThat(snap.totalOrderAmount()).isEqualByComparingTo("10000");
        }

        @Test
        @DisplayName("INSERT 분기에서 음수 like 는 0 으로 clamp 된다")
        void insertNegativeLikeClamped() {
            // given
            LocalDateTime bucket = LocalDateTime.of(2026, 4, 9, 15, 0);

            // when
            repository.upsertIncrements(1L, bucket, 0, -5, 0, BigDecimal.ZERO);

            // then
            ProductDailyAggregate snap = repository.snapshotByDate(1L, bucket.toLocalDate());
            assertThat(snap.totalLike()).isZero();
        }
    }

    @Nested
    @DisplayName("upsertIncrements — UPDATE 분기")
    class Update {

        @Test
        @DisplayName("동일 (productId, bucket) 재호출 시 델타가 누적된다")
        void accumulate() {
            // given
            LocalDateTime bucket = LocalDateTime.of(2026, 4, 9, 14, 0);
            repository.upsertIncrements(1L, bucket, 3, 1, 0, BigDecimal.ZERO);

            // when
            repository.upsertIncrements(1L, bucket, 2, 1, 1, BigDecimal.valueOf(5000));

            // then
            ProductDailyAggregate snap = repository.snapshotByDate(1L, bucket.toLocalDate());
            assertThat(snap.totalView()).isEqualTo(5);
            assertThat(snap.totalLike()).isEqualTo(2);
            assertThat(snap.totalOrder()).isEqualTo(1);
            assertThat(snap.totalOrderAmount()).isEqualByComparingTo("5000");
        }

        @Test
        @DisplayName("UPDATE 분기에서 좋아요 감소 시 0 미만이 되지 않는다 (GREATEST 가드)")
        void decrementLikeClamped() {
            // given
            LocalDateTime bucket = LocalDateTime.of(2026, 4, 9, 14, 0);
            repository.upsertIncrements(1L, bucket, 0, 1, 0, BigDecimal.ZERO);

            // when — 총 -3 취소가 들어와도 최종 like 는 0 이어야 함
            repository.upsertIncrements(1L, bucket, 0, -3, 0, BigDecimal.ZERO);

            // then
            ProductDailyAggregate snap = repository.snapshotByDate(1L, bucket.toLocalDate());
            assertThat(snap.totalLike()).isZero();
        }
    }

    @Nested
    @DisplayName("snapshotByDate — 일자 범위 집계")
    class Snapshot {

        @Test
        @DisplayName("같은 날짜의 여러 bucket 이 합산된다")
        void sameDayMultipleBuckets() {
            // given
            LocalDate date = LocalDate.of(2026, 4, 9);
            repository.upsertIncrements(1L, date.atTime(10, 0), 5, 1, 0, BigDecimal.ZERO);
            repository.upsertIncrements(1L, date.atTime(14, 0), 3, 0, 1, BigDecimal.valueOf(10000));
            repository.upsertIncrements(1L, date.atTime(20, 0), 2, 2, 0, BigDecimal.ZERO);

            // when
            ProductDailyAggregate snap = repository.snapshotByDate(1L, date);

            // then
            assertThat(snap.totalView()).isEqualTo(10);
            assertThat(snap.totalLike()).isEqualTo(3);
            assertThat(snap.totalOrder()).isEqualTo(1);
            assertThat(snap.totalOrderAmount()).isEqualByComparingTo("10000");
        }

        @Test
        @DisplayName("다른 날짜의 bucket 은 합산되지 않는다")
        void differentDayExcluded() {
            // given
            repository.upsertIncrements(1L, LocalDateTime.of(2026, 4, 8, 23, 0), 100, 0, 0, BigDecimal.ZERO);
            repository.upsertIncrements(1L, LocalDateTime.of(2026, 4, 9, 0, 0), 1, 0, 0, BigDecimal.ZERO);
            repository.upsertIncrements(1L, LocalDateTime.of(2026, 4, 10, 0, 0), 100, 0, 0, BigDecimal.ZERO);

            // when
            ProductDailyAggregate snap = repository.snapshotByDate(1L, LocalDate.of(2026, 4, 9));

            // then
            assertThat(snap.totalView()).isEqualTo(1);
        }

        @Test
        @DisplayName("데이터가 없으면 모든 값이 0 인 empty 스냅샷")
        void emptySnapshot() {
            // when
            ProductDailyAggregate snap = repository.snapshotByDate(999L, LocalDate.of(2026, 4, 9));

            // then
            assertThat(snap.totalView()).isZero();
            assertThat(snap.totalLike()).isZero();
            assertThat(snap.totalOrder()).isZero();
            assertThat(snap.totalOrderAmount()).isEqualByComparingTo("0");
        }
    }
}
