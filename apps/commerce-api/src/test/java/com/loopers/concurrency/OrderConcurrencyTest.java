package com.loopers.concurrency;

import com.loopers.application.order.OrderFacade;
import com.loopers.domain.address.UserAddress;
import com.loopers.domain.address.UserAddressRepository;
import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.inventory.Inventory;
import com.loopers.domain.inventory.InventoryRepository;
import com.loopers.domain.point.PointAccount;
import com.loopers.domain.point.PointService;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 주문 통합 동시성 테스트 (비관적 락 + 원자적 UPDATE)
 *
 * 여러 도메인(재고, 쿠폰, 포인트)이 하나의 주문 트랜잭션 안에서
 * 동시성 제어가 정상 동작하는지 검증한다.
 *
 * [동시 시작 패턴]
 * startLatch(CountDownLatch(1))를 사용하여 모든 스레드가 동시에 출발하도록 보장한다.
 */
@SpringBootTest
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class OrderConcurrencyTest {

    @Autowired
    private OrderFacade orderFacade;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private UserAddressRepository userAddressRepository;

    @Autowired
    private BrandRepository brandRepository;

    @Autowired
    private PointService pointService;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private Brand createBrand() {
        return brandRepository.save(Brand.register("테스트브랜드", "설명"));
    }

    private Product createProduct(Long brandId) {
        return productRepository.save(Product.register(brandId, "테스트상품", "설명", 10000));
    }

    @Test
    @DisplayName("재고 5개 상품에 10명이 동시에 주문하면, 재고 부족으로 정확히 5명만 성공한다")
    void 주문_동시_생성_통합_동시성() throws InterruptedException {
        // arrange
        Brand brand = createBrand();
        Product product = createProduct(brand.getId());
        inventoryRepository.save(Inventory.initialize(product.getId(), 5));

        int threadCount = 10;

        long[] addressIds = new long[threadCount];
        for (int i = 0; i < threadCount; i++) {
            long userId = i + 1L;
            UserAddress address = userAddressRepository.save(
                    UserAddress.register(userId, "수령인" + userId, "010-0000-000" + i,
                            "06234", "서울시 강남구", i + "호"));
            addressIds[i] = address.getId();

            PointAccount account = pointService.createAccount(userId);
            pointService.charge(userId, 50000);
        }

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);
        List<Exception> errors = Collections.synchronizedList(new ArrayList<>());

        // act
        for (int i = 0; i < threadCount; i++) {
            long userId = i + 1L;
            long addressId = addressIds[i];
            executor.submit(() -> {
                try {
                    startLatch.await();
                    orderFacade.createOrder(
                            userId, "주문자" + userId, "010-0000-0000",
                            List.of(new OrderFacade.OrderItemCommand(product.getId(), 1)),
                            addressId,
                            null,
                            1000,
                            "CREDIT_CARD");
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                    errors.add(e);
                } finally {
                    latch.countDown();
                }
            });
        }

        startLatch.countDown();

        latch.await();
        executor.shutdown();

        // assert
        assertThat(successCount.get()).isEqualTo(5);
        assertThat(failCount.get()).isEqualTo(5);

        Inventory inventory = inventoryRepository.findByProductId(product.getId()).orElseThrow();
        assertThat(inventory.getAvailableQuantity()).isZero();
    }
}
