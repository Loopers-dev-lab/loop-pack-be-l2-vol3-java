package com.loopers.application.cart;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandDomainService;
import com.loopers.domain.cart.Cart;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductDomainService;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest
@DisplayName("장바구니 동시성 테스트")
class CartConcurrencyIntegrationTest {

    @Autowired
    private CartApplicationService cartApplicationService;

    @Autowired
    private ProductDomainService productService;

    @Autowired
    private BrandDomainService brandService;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    private Long brandId;

    @BeforeEach
    void setUp() {
        Brand brand = brandService.register("나이키");
        brandId = brand.getId();
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("동시에 서로 다른 상품을 장바구니에 추가할 때, ")
    @Nested
    class ConcurrentAddDifferentProducts {

        @DisplayName("재시도로 모든 상품이 추가된다.")
        @Test
        void allProductsAdded_whenConcurrentWithRetry() throws InterruptedException {
            int threadCount = 10;
            Long userId = 1L;

            List<Product> products = new ArrayList<>();
            for (int i = 0; i < threadCount; i++) {
                products.add(productService.register(brandId, "상품" + i, 10000 + i));
            }

            ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
            CountDownLatch readyLatch = new CountDownLatch(threadCount);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(threadCount);
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger failCount = new AtomicInteger(0);

            for (int i = 0; i < threadCount; i++) {
                final Product product = products.get(i);
                executorService.submit(() -> {
                    readyLatch.countDown();
                    try {
                        startLatch.await();
                        cartApplicationService.addToCart(userId, product.getId(), 1);
                        successCount.incrementAndGet();
                    } catch (Exception e) {
                        failCount.incrementAndGet();
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            readyLatch.await();
            startLatch.countDown();
            doneLatch.await();
            executorService.shutdown();

            Cart cart = cartApplicationService.getMyCart(userId);

            assertAll(
                () -> assertThat(successCount.get()).isEqualTo(threadCount),
                () -> assertThat(failCount.get()).isEqualTo(0),
                () -> assertThat(cart.getItems()).hasSize(threadCount)
            );
        }
    }

    @DisplayName("동시에 최초 장바구니를 생성할 때, ")
    @Nested
    class ConcurrentFirstCartCreation {

        @DisplayName("유니크 충돌 시 재조회하여 정상 동작한다.")
        @Test
        void handlesUniqueConflictGracefully_whenConcurrentFirstCart() throws InterruptedException {
            int threadCount = 5;
            Long userId = 1L;

            List<Product> products = new ArrayList<>();
            for (int i = 0; i < threadCount; i++) {
                products.add(productService.register(brandId, "상품" + i, 10000 + i));
            }

            ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
            CountDownLatch readyLatch = new CountDownLatch(threadCount);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(threadCount);
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger failCount = new AtomicInteger(0);

            for (int i = 0; i < threadCount; i++) {
                final Product product = products.get(i);
                executorService.submit(() -> {
                    readyLatch.countDown();
                    try {
                        startLatch.await();
                        cartApplicationService.addToCart(userId, product.getId(), 1);
                        successCount.incrementAndGet();
                    } catch (Exception e) {
                        failCount.incrementAndGet();
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            readyLatch.await();
            startLatch.countDown();
            doneLatch.await();
            executorService.shutdown();

            Cart cart = cartApplicationService.getMyCart(userId);

            assertAll(
                () -> assertThat(successCount.get()).isEqualTo(threadCount),
                () -> assertThat(failCount.get()).isEqualTo(0),
                () -> assertThat(cart.getItems()).hasSizeGreaterThanOrEqualTo(1)
            );
        }
    }
}
