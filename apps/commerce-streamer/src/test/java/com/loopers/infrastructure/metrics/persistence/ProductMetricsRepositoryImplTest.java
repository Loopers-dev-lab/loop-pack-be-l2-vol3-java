package com.loopers.infrastructure.metrics.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.support.TransactionTemplate;

import com.loopers.domain.metrics.ProductMetrics;
import com.loopers.utils.DatabaseCleanUp;

@SpringBootTest
class ProductMetricsRepositoryImplTest {

    @Autowired
    private ProductMetricsRepositoryImpl productMetricsRepository;

    @Autowired
    private ProductMetricsJpaRepository productMetricsJpaRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    private static final LocalDate CURRENT_METRIC_DATE = LocalDate.now();

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private void upsertLikeCount(Long productId, Long delta) {
        transactionTemplate.executeWithoutResult(status ->
                productMetricsRepository.upsertLikeCount(productId, delta)
        );
    }

    private void upsertOrderCount(Long productId, Long quantity) {
        transactionTemplate.executeWithoutResult(status ->
                productMetricsRepository.upsertOrderCount(productId, quantity)
        );
    }

    private void upsertViewCount(Long productId) {
        transactionTemplate.executeWithoutResult(status ->
                productMetricsRepository.upsertViewCount(productId)
        );
    }

    @DisplayName("좋아요 수를 upsert할 때,")
    @Nested
    class UpsertLikeCount {

        @DisplayName("행이 없으면, 새로 생성하고 좋아요 수를 설정한다.")
        @Test
        void insertsNewRow_whenNotExists() {
            // act
            upsertLikeCount(1L, 1L);

            // assert
            List<ProductMetrics> results = productMetricsJpaRepository.findAll();
            assertAll(
                    () -> assertThat(results).hasSize(1),
                    () -> assertThat(results.get(0).getProductId()).isEqualTo(1L),
                    () -> assertThat(results.get(0).getLikeCount()).isEqualTo(1L),
                    () -> assertThat(results.get(0).getMetricDate()).isEqualTo(CURRENT_METRIC_DATE)
            );
        }

        @DisplayName("행이 있으면, 좋아요 수를 증가시킨다.")
        @Test
        void incrementsExistingRow() {
            // arrange
            upsertLikeCount(1L, 1L);

            // act
            upsertLikeCount(1L, 1L);

            // assert
            List<ProductMetrics> results = productMetricsJpaRepository.findAll();
            assertAll(
                    () -> assertThat(results).hasSize(1),
                    () -> assertThat(results.get(0).getLikeCount()).isEqualTo(2L)
            );
        }

        @DisplayName("좋아요 취소 시, 좋아요 수가 0 미만으로 내려가지 않는다.")
        @Test
        void doesNotGoBelowZero() {
            // arrange
            upsertLikeCount(1L, 1L);

            // act
            upsertLikeCount(1L, -1L);
            upsertLikeCount(1L, -1L);

            // assert
            List<ProductMetrics> results = productMetricsJpaRepository.findAll();
            assertThat(results.get(0).getLikeCount()).isZero();
        }
    }

    @DisplayName("주문 수량을 upsert할 때,")
    @Nested
    class UpsertOrderCount {

        @DisplayName("행이 없으면, 새로 생성하고 주문 수량을 설정한다.")
        @Test
        void insertsNewRow_whenNotExists() {
            // act
            upsertOrderCount(1L, 3L);

            // assert
            List<ProductMetrics> results = productMetricsJpaRepository.findAll();
            assertAll(
                    () -> assertThat(results).hasSize(1),
                    () -> assertThat(results.get(0).getProductId()).isEqualTo(1L),
                    () -> assertThat(results.get(0).getOrderCount()).isEqualTo(3L),
                    () -> assertThat(results.get(0).getMetricDate()).isEqualTo(CURRENT_METRIC_DATE)
            );
        }

        @DisplayName("행이 있으면, 주문 수량을 누적한다.")
        @Test
        void accumulatesQuantity() {
            // arrange
            upsertOrderCount(1L, 2L);

            // act
            upsertOrderCount(1L, 5L);

            // assert
            List<ProductMetrics> results = productMetricsJpaRepository.findAll();
            assertAll(
                    () -> assertThat(results).hasSize(1),
                    () -> assertThat(results.get(0).getOrderCount()).isEqualTo(7L)
            );
        }
    }

    @DisplayName("조회 수를 upsert할 때,")
    @Nested
    class UpsertViewCount {

        @DisplayName("행이 없으면, 새로 생성하고 조회 수를 1로 설정한다.")
        @Test
        void insertsNewRow_whenNotExists() {
            // act
            upsertViewCount(1L);

            // assert
            List<ProductMetrics> results = productMetricsJpaRepository.findAll();
            assertAll(
                    () -> assertThat(results).hasSize(1),
                    () -> assertThat(results.get(0).getProductId()).isEqualTo(1L),
                    () -> assertThat(results.get(0).getViewCount()).isEqualTo(1L),
                    () -> assertThat(results.get(0).getMetricDate()).isEqualTo(CURRENT_METRIC_DATE)
            );
        }

        @DisplayName("행이 있으면, 조회 수를 1 증가시킨다.")
        @Test
        void incrementsExistingRow() {
            // arrange
            upsertViewCount(1L);

            // act
            upsertViewCount(1L);

            // assert
            List<ProductMetrics> results = productMetricsJpaRepository.findAll();
            assertAll(
                    () -> assertThat(results).hasSize(1),
                    () -> assertThat(results.get(0).getViewCount()).isEqualTo(2L)
            );
        }
    }
}
