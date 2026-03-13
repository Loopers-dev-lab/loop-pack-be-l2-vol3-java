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
        int initialStock = 100;
        int threadCount = 100;
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
        int initialStock = 50;
        int threadCount = 100;

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

    @DisplayName("상품을 역순으로 주문해도 데드락 없이 모두 성공한다")
    @Test
    void concurrentOrders_reverseProductOrder_noDeadlock() throws InterruptedException {
        // arrange
        Brand brand = brandRepository.save(new Brand("나이키", "스포츠 브랜드"));
        Product productA = productRepository.save(
            new Product(brand.getId(), "에어맥스", new Price(100000), new Stock(10)));
        Product productB = productRepository.save(
            new Product(brand.getId(), "덩크로우", new Price(120000), new Stock(10)));

        Long idA = productA.getId();
        Long idB = productB.getId();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(2);
        AtomicInteger successCount = new AtomicInteger(0);

        // act: 유저1은 [A, B] 순서, 유저2는 [B, A] 순서로 요청
        executor.submit(() -> {
            ready.countDown();
            try { start.await(); } catch (InterruptedException ignored) {}
            try {
                orderFacade.createOrder(1L,
                    List.of(new OrderFacade.OrderItemRequest(idA, 1),
                            new OrderFacade.OrderItemRequest(idB, 1)));
                successCount.incrementAndGet();
            } catch (Exception ignored) {
            } finally { done.countDown(); }
        });

        executor.submit(() -> {
            ready.countDown();
            try { start.await(); } catch (InterruptedException ignored) {}
            try {
                orderFacade.createOrder(2L,
                    List.of(new OrderFacade.OrderItemRequest(idB, 1),
                            new OrderFacade.OrderItemRequest(idA, 1)));
                successCount.incrementAndGet();
            } catch (Exception ignored) {
            } finally { done.countDown(); }
        });

        ready.await();
        start.countDown(); // 두 스레드 동시 출발
        done.await();
        executor.shutdown();

        // assert: 데드락 없이 둘 다 성공, 각 상품 재고 2씩 차감 (2명 × 1개)
        assertThat(successCount.get()).isEqualTo(2);

        Product reloadedA = productRepository.findById(idA).orElseThrow();
        Product reloadedB = productRepository.findById(idB).orElseThrow();
        assertThat(reloadedA.getStock().getQuantity()).isEqualTo(8);
        assertThat(reloadedB.getStock().getQuantity()).isEqualTo(8);
    }
}
