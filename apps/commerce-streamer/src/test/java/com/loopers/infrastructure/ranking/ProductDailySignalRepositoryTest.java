package com.loopers.infrastructure.ranking;

import com.loopers.domain.ranking.ProductDailySignalModel;
import com.loopers.domain.ranking.ProductDailySignalRepository;
import com.loopers.testcontainers.MySqlTestContainersConfig;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(MySqlTestContainersConfig.class)
@DisplayName("ProductDailySignalRepository 통합 테스트")
class ProductDailySignalRepositoryTest {

    @Autowired
    private ProductDailySignalRepository repository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    private static final LocalDate DATE = LocalDate.of(2026, 4, 8);

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @Nested
    @DisplayName("Upsert 동작")
    class Upsert {

        @Test
        @DisplayName("최초 upsert는 새 row를 생성한다")
        void firstUpsertCreatesNewRow() {
            repository.upsertViewCount(1L, DATE, 1);

            List<ProductDailySignalModel> signals = repository.findBySignalDate(DATE);
            assertThat(signals).hasSize(1);
            assertThat(signals.get(0).getProductDbId()).isEqualTo(1L);
            assertThat(signals.get(0).getViewCount()).isEqualTo(1L);
            assertThat(signals.get(0).getLikeCount()).isEqualTo(0L);
            assertThat(signals.get(0).getOrderAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("동일 상품+날짜에 view upsert를 반복하면 카운트가 누적된다")
        void repeatedViewUpsertAccumulates() {
            repository.upsertViewCount(1L, DATE, 1);
            repository.upsertViewCount(1L, DATE, 1);
            repository.upsertViewCount(1L, DATE, 1);

            List<ProductDailySignalModel> signals = repository.findBySignalDate(DATE);
            assertThat(signals).hasSize(1);
            assertThat(signals.get(0).getViewCount()).isEqualTo(3L);
        }

        @Test
        @DisplayName("like upsert는 like_count만 증가시킨다")
        void likeUpsertOnlyAffectsLikeCount() {
            repository.upsertViewCount(1L, DATE, 5);
            repository.upsertLikeCount(1L, DATE, 3);

            List<ProductDailySignalModel> signals = repository.findBySignalDate(DATE);
            assertThat(signals).hasSize(1);
            assertThat(signals.get(0).getViewCount()).isEqualTo(5L);
            assertThat(signals.get(0).getLikeCount()).isEqualTo(3L);
        }

        @Test
        @DisplayName("order upsert는 order_amount만 증가시킨다")
        void orderUpsertOnlyAffectsOrderAmount() {
            repository.upsertViewCount(1L, DATE, 1);
            repository.upsertOrderAmount(1L, DATE, new BigDecimal("15000.50"));

            List<ProductDailySignalModel> signals = repository.findBySignalDate(DATE);
            assertThat(signals).hasSize(1);
            assertThat(signals.get(0).getViewCount()).isEqualTo(1L);
            assertThat(signals.get(0).getOrderAmount()).isEqualByComparingTo(new BigDecimal("15000.50"));
        }

        @Test
        @DisplayName("같은 상품이라도 날짜가 다르면 별도 row가 생성된다")
        void differentDatesCreateSeparateRows() {
            LocalDate yesterday = DATE.minusDays(1);
            repository.upsertViewCount(1L, DATE, 10);
            repository.upsertViewCount(1L, yesterday, 5);

            List<ProductDailySignalModel> todaySignals = repository.findBySignalDate(DATE);
            List<ProductDailySignalModel> yesterdaySignals = repository.findBySignalDate(yesterday);
            assertThat(todaySignals).hasSize(1);
            assertThat(yesterdaySignals).hasSize(1);
            assertThat(todaySignals.get(0).getViewCount()).isEqualTo(10L);
            assertThat(yesterdaySignals.get(0).getViewCount()).isEqualTo(5L);
        }
    }

    @Nested
    @DisplayName("동시성")
    class Concurrency {

        @Test
        @DisplayName("동시 upsert 시 카운트 정합성이 유지된다")
        void concurrentUpsertMaintainsConsistency() throws InterruptedException {
            int threadCount = 10;
            int iterationsPerThread = 100;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(threadCount);

            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        for (int j = 0; j < iterationsPerThread; j++) {
                            repository.upsertViewCount(1L, DATE, 1);
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            doneLatch.await();
            executor.shutdown();

            List<ProductDailySignalModel> signals = repository.findBySignalDate(DATE);
            assertThat(signals).hasSize(1);
            assertThat(signals.get(0).getViewCount()).isEqualTo((long) threadCount * iterationsPerThread);
        }
    }

    @Nested
    @DisplayName("조회")
    class FindByDate {

        @Test
        @DisplayName("해당 날짜의 모든 상품 신호를 조회한다")
        void findsAllSignalsForDate() {
            repository.upsertViewCount(1L, DATE, 10);
            repository.upsertViewCount(2L, DATE, 20);
            repository.upsertViewCount(3L, DATE, 30);

            List<ProductDailySignalModel> signals = repository.findBySignalDate(DATE);
            assertThat(signals).hasSize(3);
        }

        @Test
        @DisplayName("데이터가 없는 날짜는 빈 리스트를 반환한다")
        void returnsEmptyForNoData() {
            List<ProductDailySignalModel> signals = repository.findBySignalDate(DATE);
            assertThat(signals).isEmpty();
        }
    }
}
