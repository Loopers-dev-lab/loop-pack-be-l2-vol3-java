package com.loopers.domain.product;

import com.loopers.support.error.CoreException;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("재고 동시성 테스트")
class StockConcurrencyTest {

    @Autowired
    StockService stockService;

    @Autowired
    ProductStockRepository stockRepository;

    @Autowired
    DatabaseCleanUp databaseCleanUp;

    private Long productId;

    @BeforeEach
    void setUp() {
        ProductStockModel stock = stockService.createStock(1L, 10);
        productId = stock.getProductId();
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @Test
    @DisplayName("20개 스레드가 동시에 hold(1) 시, 재고 10개에 대해 정확히 10개만 성공한다")
    void concurrentHold_ShouldNotOversell() throws InterruptedException {
        int threadCount = 20;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    stockService.hold(productId, 1);
                    successCount.incrementAndGet();
                } catch (CoreException e) {
                    // STOCK_NOT_ENOUGH 예상
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

        assertThat(successCount.get()).isEqualTo(10);
        ProductStockModel stock = stockRepository.findByProductId(productId).get();
        assertThat(stock.getAvailableQty()).isEqualTo(0);
        assertThat(stock.getReserved()).isEqualTo(10);
    }

    @Test
    @DisplayName("hold와 release가 동시에 실행되어도 재고 정합성이 유지된다")
    void concurrentHoldAndRelease_ShouldMaintainConsistency() throws InterruptedException {
        // 먼저 5개를 예약해둠
        stockService.hold(productId, 5);

        int holdThreads = 10;
        int releaseThreads = 5;
        int totalThreads = holdThreads + releaseThreads;
        ExecutorService executor = Executors.newFixedThreadPool(totalThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(totalThreads);
        AtomicInteger holdSuccess = new AtomicInteger(0);
        AtomicInteger releaseSuccess = new AtomicInteger(0);

        // hold 스레드들: 각각 1개씩 hold 시도
        for (int i = 0; i < holdThreads; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    stockService.hold(productId, 1);
                    holdSuccess.incrementAndGet();
                } catch (CoreException e) {
                    // STOCK_NOT_ENOUGH
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        // release 스레드들: 각각 1개씩 release 시도
        for (int i = 0; i < releaseThreads; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    stockService.release(productId, 1);
                    releaseSuccess.incrementAndGet();
                } catch (CoreException e) {
                    // release 실패
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

        ProductStockModel stock = stockRepository.findByProductId(productId).get();

        // 최종 reserved = 초기reserved(5) + holdSuccess - releaseSuccess
        int expectedReserved = 5 + holdSuccess.get() - releaseSuccess.get();
        assertThat(stock.getReserved()).isEqualTo(expectedReserved);
        assertThat(stock.getOnHand()).isEqualTo(10);
        assertThat(stock.getAvailableQty()).isGreaterThanOrEqualTo(0);
    }
}
