package com.loopers.interfaces.api;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
import com.loopers.interfaces.api.order.OrderV1Dto;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("주문 동시성 테스트")
class OrderConcurrencyTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @Autowired
    private BrandRepository brandRepository;

    @Autowired
    private ProductRepository productRepository;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @Test
    @DisplayName("여러 고객이 동시에 같은 상품을 주문해도 재고 정합성이 보장된다")
    void concurrentOrders_StockConsistency() throws InterruptedException {
        // Given
        Brand brand = brandRepository.save(Brand.create("샤넬", null, null));
        Product product = productRepository.save(Product.create(brand.getId(), "상품", null, new BigDecimal("10000"), 10, null));

        int threadCount = 10;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        // When - 10명이 동시에 각 1개씩 주문 (재고 10개)
        for (int i = 0; i < threadCount; i++) {
            long userId = i + 1;
            executorService.submit(() -> {
                try {
                    HttpHeaders headers = new HttpHeaders();
                    headers.set("X-User-Id", String.valueOf(userId));

                    OrderV1Dto.CreateRequest request = new OrderV1Dto.CreateRequest(
                            List.of(new OrderV1Dto.OrderItemRequest(product.getId(), 1)),
                            null
                    );

                    ResponseEntity<ApiResponse<OrderV1Dto.Response>> response = restTemplate.exchange(
                            "/api/v1/orders",
                            HttpMethod.POST,
                            new HttpEntity<>(request, headers),
                            new ParameterizedTypeReference<>() {}
                    );

                    if (response.getStatusCode() == HttpStatus.OK) {
                        successCount.incrementAndGet();
                    } else {
                        failCount.incrementAndGet();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executorService.shutdown();

        // Then - 모든 주문 성공, 재고 0
        Product updatedProduct = productRepository.findById(product.getId()).orElseThrow();
        assertThat(successCount.get()).isEqualTo(10);
        assertThat(failCount.get()).isEqualTo(0);
        assertThat(updatedProduct.getStock()).isEqualTo(0);
    }

    @Test
    @DisplayName("재고보다 많은 동시 주문이 들어오면 일부만 성공하고 재고는 음수가 되지 않는다")
    void concurrentOrders_ExceedStock_NoOverselling() throws InterruptedException {
        // Given
        Brand brand = brandRepository.save(Brand.create("샤넬", null, null));
        Product product = productRepository.save(Product.create(brand.getId(), "상품", null, new BigDecimal("10000"), 5, null));

        int threadCount = 10;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        // When - 10명이 동시에 각 1개씩 주문 (재고 5개)
        for (int i = 0; i < threadCount; i++) {
            long userId = i + 1;
            executorService.submit(() -> {
                try {
                    HttpHeaders headers = new HttpHeaders();
                    headers.set("X-User-Id", String.valueOf(userId));

                    OrderV1Dto.CreateRequest request = new OrderV1Dto.CreateRequest(
                            List.of(new OrderV1Dto.OrderItemRequest(product.getId(), 1)),
                            null
                    );

                    ResponseEntity<ApiResponse<OrderV1Dto.Response>> response = restTemplate.exchange(
                            "/api/v1/orders",
                            HttpMethod.POST,
                            new HttpEntity<>(request, headers),
                            new ParameterizedTypeReference<>() {}
                    );

                    if (response.getStatusCode() == HttpStatus.OK) {
                        successCount.incrementAndGet();
                    } else {
                        failCount.incrementAndGet();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executorService.shutdown();

        // Then - 5개만 성공, 5개 실패, 재고 0 (음수 아님)
        Product updatedProduct = productRepository.findById(product.getId()).orElseThrow();
        assertThat(successCount.get()).isEqualTo(5);
        assertThat(failCount.get()).isEqualTo(5);
        assertThat(updatedProduct.getStock()).isEqualTo(0);
    }
}
