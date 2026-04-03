package com.loopers.concurrency;

import com.loopers.application.order.OrderService;
import com.loopers.domain.member.Member;
import com.loopers.domain.member.MemberRepository;
import com.loopers.domain.order.OrderDomainService;
import com.loopers.domain.product.Brand;
import com.loopers.domain.product.BrandRepository;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class StockConcurrencyTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private BrandRepository brandRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("재고 10개인 상품에 10명이 동시에 1개씩 주문하면 재고가 0이 된다")
    @Test
    void concurrentStockDecrease_allSucceed() throws InterruptedException {
        Brand brand = brandRepository.save(new Brand("테스트 브랜드"));
        Product product = productRepository.save(new Product(brand.getId(), "상품", 10_000L, 10));
        Long productId = product.getId();

        int threadCount = 10;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        List<Boolean> results = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < threadCount; i++) {
            final long memberId = i + 1;
            memberRepository.save(new Member("user" + memberId, "password", "사용자" + memberId, "2000-01-01", "user" + memberId + "@test.com"));
            // 대기열 입장 허가 키 세팅 (동시성 테스트에서 대기열 검증 통과용)
            redisTemplate.opsForValue().set("entered:" + memberId, "1", 300, java.util.concurrent.TimeUnit.SECONDS);
            executorService.submit(() -> {
                try {
                    orderService.placeOrder(memberId, List.of(
                        new OrderDomainService.OrderLineRequest(productId, 1)
                    ), null);
                    results.add(true);
                } catch (Exception e) {
                    results.add(false);
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executorService.shutdown();

        long successCount = results.stream().filter(r -> r).count();
        assertThat(successCount).isEqualTo(10);

        Product updated = productRepository.findById(productId).orElseThrow();
        assertThat(updated.getStockQuantity()).isEqualTo(0);
    }

    @DisplayName("재고 5개인 상품에 10명이 동시에 1개씩 주문하면 5건만 성공하고 재고가 0이 된다")
    @Test
    void concurrentStockDecrease_partialSuccess() throws InterruptedException {
        Brand brand = brandRepository.save(new Brand("테스트 브랜드"));
        Product product = productRepository.save(new Product(brand.getId(), "상품", 10_000L, 5));
        Long productId = product.getId();

        int threadCount = 10;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        List<Boolean> results = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < threadCount; i++) {
            final long memberId = i + 1;
            memberRepository.save(new Member("user" + memberId, "password", "사용자" + memberId, "2000-01-01", "user" + memberId + "@test.com"));
            redisTemplate.opsForValue().set("entered:" + memberId, "1", 300, java.util.concurrent.TimeUnit.SECONDS);
            executorService.submit(() -> {
                try {
                    orderService.placeOrder(memberId, List.of(
                        new OrderDomainService.OrderLineRequest(productId, 1)
                    ), null);
                    results.add(true);
                } catch (Exception e) {
                    results.add(false);
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executorService.shutdown();

        long successCount = results.stream().filter(r -> r).count();
        long failCount = results.stream().filter(r -> !r).count();
        assertThat(successCount).isEqualTo(5);
        assertThat(failCount).isEqualTo(5);

        Product updated = productRepository.findById(productId).orElseThrow();
        assertThat(updated.getStockQuantity()).isEqualTo(0);
    }
}
