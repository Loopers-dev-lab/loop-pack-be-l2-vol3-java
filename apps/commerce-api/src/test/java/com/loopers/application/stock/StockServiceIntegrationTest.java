package com.loopers.application.stock;

import com.loopers.domain.stock.Stock;
import com.loopers.domain.stock.StockRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class StockServiceIntegrationTest {

    @Autowired
    private StockService stockService;

    @Autowired
    private StockRepository stockRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @BeforeEach
    void setUp() {
        databaseCleanUp.truncateAllTables();
    }

    @Nested
    class 재고_점유 {

        @Test
        void 가용_재고가_충분하면_점유에_성공한다() {
            stockRepository.save(Stock.create(1L, 100));
            stockRepository.save(Stock.create(2L, 50));

            stockService.reserve(Map.of(1L, 30, 2L, 20));

            Stock result1 = stockRepository.findByProductId(1L).orElseThrow();
            Stock result2 = stockRepository.findByProductId(2L).orElseThrow();
            assertAll(
                    () -> assertThat(result1.getReservedQuantity()).isEqualTo(30),
                    () -> assertThat(result1.getAvailableQuantity()).isEqualTo(70),
                    () -> assertThat(result2.getReservedQuantity()).isEqualTo(20),
                    () -> assertThat(result2.getAvailableQuantity()).isEqualTo(30)
            );
        }

        @Test
        void 하나라도_가용_재고가_부족하면_전체_점유가_실패한다() {
            stockRepository.save(Stock.create(1L, 100));
            stockRepository.save(Stock.create(2L, 5));

            assertThatThrownBy(() -> stockService.reserve(Map.of(1L, 30, 2L, 10)))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));

            Stock result1 = stockRepository.findByProductId(1L).orElseThrow();
            Stock result2 = stockRepository.findByProductId(2L).orElseThrow();
            assertAll(
                    () -> assertThat(result1.getReservedQuantity()).isEqualTo(0),
                    () -> assertThat(result2.getReservedQuantity()).isEqualTo(0)
            );
        }

        @Test
        void 재고_정보가_존재하지_않으면_예외() {
            stockRepository.save(Stock.create(1L, 100));

            assertThatThrownBy(() -> stockService.reserve(Map.of(1L, 10, 999L, 5)))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> {
                        CoreException ce = (CoreException) e;
                        assertThat(ce.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
                    });
        }

        @Test
        void 동시에_같은_상품에_점유_요청이_들어와도_가용_재고를_초과하지_않는다() throws InterruptedException {
            stockRepository.save(Stock.create(1L, 10));

            int threadCount = 10;
            ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
            CountDownLatch latch = new CountDownLatch(threadCount);
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger failCount = new AtomicInteger(0);

            for (int i = 0; i < threadCount; i++) {
                executorService.submit(() -> {
                    try {
                        stockService.reserve(Map.of(1L, 3));
                        successCount.incrementAndGet();
                    } catch (CoreException e) {
                        failCount.incrementAndGet();
                    } finally {
                        latch.countDown();
                    }
                });
            }

            latch.await();
            executorService.shutdown();

            Stock result = stockRepository.findByProductId(1L).orElseThrow();
            assertAll(
                    () -> assertThat(successCount.get()).isEqualTo(3),
                    () -> assertThat(failCount.get()).isEqualTo(7),
                    () -> assertThat(result.getReservedQuantity()).isEqualTo(9),
                    () -> assertThat(result.getAvailableQuantity()).isEqualTo(1)
            );
        }
    }

    @Nested
    class 재고_확정 {

        @Test
        void 점유된_재고를_확정하면_점유_수량이_감소하고_확정_차감_수량이_증가한다() {
            stockRepository.save(Stock.create(1L, 100));
            stockService.reserve(Map.of(1L, 30));

            stockService.confirm(Map.of(1L, 30));

            Stock result = stockRepository.findByProductId(1L).orElseThrow();
            assertAll(
                    () -> assertThat(result.getReservedQuantity()).isEqualTo(0),
                    () -> assertThat(result.getConfirmedQuantity()).isEqualTo(30),
                    () -> assertThat(result.getAvailableQuantity()).isEqualTo(70),
                    () -> assertThat(result.getQuantity()).isEqualTo(100)
            );
        }
    }
}
