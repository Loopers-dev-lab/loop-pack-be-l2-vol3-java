package com.loopers.concurrency;

import com.loopers.application.order.OrderFacade;
import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.product.vo.Price;
import com.loopers.domain.product.vo.Stock;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class StockConcurrencyTest {

    @Autowired
    private OrderFacade orderFacade;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private BrandRepository brandRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("동일 상품에 여러 주문이 동시에 요청되면 재고가 정확히 차감된다")
    @Test
    void concurrentOrders_decreasesStockCorrectly() throws InterruptedException {
        // arrange
        int initialStock = 10;
        int threadCount = 10;
        int orderQuantity = 1;

        Brand brand = brandRepository.save(new Brand("나이키", "스포츠 브랜드"));
        Product product = productRepository.save(
            new Product(brand.getId(), "에어맥스", new Price(100000), new Stock(initialStock)));
        Long productId = product.getId();

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        // act
        for (int i = 0; i < threadCount; i++) {
            long memberId = i + 1;
            executor.submit(() -> {
                try {
                    orderFacade.createOrder(memberId,
                        List.of(new OrderFacade.OrderItemRequest(productId, orderQuantity)));
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }
        latch.await();
        executor.shutdown();

        // assert
        Product reloaded = productRepository.findById(productId).orElseThrow();
        assertThat(successCount.get()).isEqualTo(initialStock);
        assertThat(failCount.get()).isEqualTo(0);
        assertThat(reloaded.getStock().getQuantity()).isEqualTo(0);
    }

    @DisplayName("재고보다 많은 동시 주문이 요청되면 재고만큼만 성공한다")
    @Test
    void concurrentOrders_exceedingStock_failsGracefully() throws InterruptedException {
        // arrange
        int initialStock = 5;
        int threadCount = 10;

        Brand brand = brandRepository.save(new Brand("나이키", "스포츠 브랜드"));
        Product product = productRepository.save(
            new Product(brand.getId(), "에어맥스", new Price(100000), new Stock(initialStock)));
        Long productId = product.getId();

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        // act
        for (int i = 0; i < threadCount; i++) {
            long memberId = i + 1;
            executor.submit(() -> {
                try {
                    orderFacade.createOrder(memberId,
                        List.of(new OrderFacade.OrderItemRequest(productId, 1)));
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }
        latch.await();
        executor.shutdown();

        // assert
        Product reloaded = productRepository.findById(productId).orElseThrow();
        assertThat(successCount.get()).isEqualTo(initialStock);
        assertThat(failCount.get()).isEqualTo(threadCount - initialStock);
        assertThat(reloaded.getStock().getQuantity()).isEqualTo(0);
    }
}
