package com.loopers.concurrency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.loopers.application.order.OrderFacade;
import com.loopers.application.order.dto.OrderCriteria;
import com.loopers.application.order.dto.OrderResult;
import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.order.OrderItemModel;
import com.loopers.domain.order.OrderItemStatus;
import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderStatus;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.user.UserModel;
import com.loopers.infrastructure.brand.BrandJpaRepository;
import com.loopers.infrastructure.order.OrderItemJpaRepository;
import com.loopers.infrastructure.order.OrderJpaRepository;
import com.loopers.infrastructure.product.ProductJpaRepository;
import com.loopers.infrastructure.user.UserJpaRepository;
import com.loopers.utils.DatabaseCleanUp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@DisplayName("주문 아이템 동시 취소 동시성 테스트")
@SpringBootTest
class OrderItemCancelConcurrencyTest {

    private static final int THREAD_COUNT = 3;

    @Autowired
    private OrderFacade orderFacade;

    @Autowired
    private UserJpaRepository userJpaRepository;

    @Autowired
    private BrandJpaRepository brandJpaRepository;

    @Autowired
    private ProductJpaRepository productJpaRepository;

    @Autowired
    private OrderJpaRepository orderJpaRepository;

    @Autowired
    private OrderItemJpaRepository orderItemJpaRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private UserModel createUserWithPoint(String loginId, long point) {
        UserModel user = UserModel.create(
                loginId, "encrypted", "테스트", java.time.LocalDate.of(1990, 1, 1),
                loginId + "@test.com");
        user.addPoint(point);
        return userJpaRepository.save(user);
    }

    private ProductModel createProduct(Long brandId, String name, int price, int stock) {
        return productJpaRepository.save(
                ProductModel.create(brandId, name, price, stock));
    }

    @DisplayName("3개 아이템 주문 후 동시에 3개 모두 취소 -> totalPrice 정합성 유지, 전체 취소 상태")
    @RepeatedTest(3)
    void concurrentCancelItems_allCancelled_totalPriceZero() throws InterruptedException {
        // arrange
        BrandModel brand = brandJpaRepository.save(BrandModel.create("브랜드A"));
        Long brandId = brand.getId();

        ProductModel product1 = createProduct(brandId, "상품A", 10000, 100);
        ProductModel product2 = createProduct(brandId, "상품B", 20000, 100);
        ProductModel product3 = createProduct(brandId, "상품C", 30000, 100);

        UserModel user = createUserWithPoint("canceluser", 1_000_000);

        OrderResult.OrderSummary orderSummary = orderFacade.createOrder(
                user.getId(),
                new OrderCriteria.Create(List.of(
                        new OrderCriteria.Create.CreateItem(product1.getId(), 1, 10000),
                        new OrderCriteria.Create.CreateItem(product2.getId(), 1, 20000),
                        new OrderCriteria.Create.CreateItem(product3.getId(), 1, 30000))));

        Long orderId = orderSummary.orderId();
        List<OrderItemModel> orderItems = orderItemJpaRepository.findAllByOrderId(orderId);
        List<Long> orderItemIds = orderItems.stream()
                .map(OrderItemModel::getId)
                .toList();

        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
        CountDownLatch readyLatch = new CountDownLatch(THREAD_COUNT);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(THREAD_COUNT);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);
        List<String> errors = Collections.synchronizedList(new ArrayList<>());

        // act
        for (int i = 0; i < THREAD_COUNT; i++) {
            final Long orderItemId = orderItemIds.get(i);
            executor.submit(() -> {
                try {
                    readyLatch.countDown();
                    startLatch.await();
                    orderFacade.cancelMyOrderItem(
                            user.getId(), orderId, orderItemId);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                    errors.add(e.getClass().getSimpleName() + ": " + e.getMessage());
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        readyLatch.await();
        startLatch.countDown();
        doneLatch.await();
        executor.shutdown();

        // assert
        OrderModel updatedOrder = orderJpaRepository.findById(orderId).orElseThrow();
        List<OrderItemModel> updatedItems =
                orderItemJpaRepository.findAllByOrderId(orderId);
        ProductModel updatedProduct1 =
                productJpaRepository.findById(product1.getId()).orElseThrow();
        ProductModel updatedProduct2 =
                productJpaRepository.findById(product2.getId()).orElseThrow();
        ProductModel updatedProduct3 =
                productJpaRepository.findById(product3.getId()).orElseThrow();

        System.out.println("\n========== 동시 아이템 취소 ==========");
        System.out.println("  스레드 수              : " + THREAD_COUNT);
        System.out.println("  성공 카운트            : " + successCount.get());
        System.out.println("  실패 카운트            : " + failCount.get());
        System.out.println("  주문 상태              : " + updatedOrder.getStatus());
        System.out.println("  주문 총액              : " + updatedOrder.getTotalPrice());
        System.out.println("  상품A 재고             : " + updatedProduct1.getStock());
        System.out.println("  상품B 재고             : " + updatedProduct2.getStock());
        System.out.println("  상품C 재고             : " + updatedProduct3.getStock());
        if (!errors.isEmpty()) {
            System.out.println("  에러 목록              : " + errors);
        }
        System.out.println("=====================================\n");

        assertAll(
                () -> assertThat(successCount.get())
                        .as("3건 모두 성공해야 한다")
                        .isEqualTo(THREAD_COUNT),
                () -> assertThat(updatedOrder.getStatus())
                        .as("주문 상태가 CANCELLED여야 한다")
                        .isEqualTo(OrderStatus.CANCELLED),
                () -> assertThat(updatedOrder.getTotalPrice())
                        .as("주문 총액이 0이어야 한다")
                        .isEqualTo(0),
                () -> assertThat(updatedItems)
                        .as("모든 아이템이 CANCELLED 상태여야 한다")
                        .allMatch(item -> item.getStatus() == OrderItemStatus.CANCELLED),
                () -> assertThat(updatedProduct1.getStock())
                        .as("상품A 재고가 원복되어야 한다")
                        .isEqualTo(100),
                () -> assertThat(updatedProduct2.getStock())
                        .as("상품B 재고가 원복되어야 한다")
                        .isEqualTo(100),
                () -> assertThat(updatedProduct3.getStock())
                        .as("상품C 재고가 원복되어야 한다")
                        .isEqualTo(100));
    }
}
