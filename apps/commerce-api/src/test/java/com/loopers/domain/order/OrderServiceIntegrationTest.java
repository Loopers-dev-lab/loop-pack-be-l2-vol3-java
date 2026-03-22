package com.loopers.domain.order;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.product.ProductValidationRequest;
import com.loopers.domain.product.Quantity;
import com.loopers.domain.product.Money;
import com.loopers.domain.product.StockQuantity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
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
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@Import(MySqlTestContainersConfig.class)
class OrderServiceIntegrationTest {

    private static final Long USER_ID = 1L;
    private static final Long OTHER_USER_ID = 2L;

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderRepository orderRepository;

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

    private Long saveProduct(String name, int stock) {
        BrandModel brand = BrandModel.create("브랜드");
        brandRepository.save(brand);
        ProductModel product = ProductModel.create(brand.getId(), name, Money.of(new BigDecimal("10000")),
                StockQuantity.of(stock));
        return productRepository.save(product).getId();
    }

    @DisplayName("create 시")
    @Nested
    class Create {

        @Test
        void create_withValidRequests_shouldSaveAndReturnOrder() {
            // given
            Long productId = saveProduct("상품", 10);
            List<ProductValidationRequest> requests = List
                    .of(new ProductValidationRequest(productId, Quantity.of(2), null));

            // when
            OrderModel saved = orderService.create(USER_ID, requests);

            // then
            assertThat(saved.getId()).isNotNull();
            assertThat(saved.getUserId()).isEqualTo(USER_ID);
            assertThat(saved.getStatus()).isEqualTo(OrderStatus.ORDERED);
            assertThat(saved.getOrderItems()).hasSize(1);
            assertThat(saved.getOrderItems().get(0).getProductId()).isEqualTo(productId);
            assertThat(saved.getOrderItems().get(0).getQuantity()).isEqualTo(2);

            Optional<OrderModel> found = orderRepository.findById(saved.getId());
            assertThat(found).isPresent();
        }

        @Test
        void create_whenProductNotFound_shouldThrowNotFound() {
            List<ProductValidationRequest> requests = List
                    .of(new ProductValidationRequest(999_999L, Quantity.of(1), null));
            CoreException ex = assertThrows(CoreException.class, () -> orderService.create(USER_ID, requests));
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }

        @Test
        void create_whenInsufficientStock_shouldThrowBadRequest() {
            Long productId = saveProduct("상품", 1);
            List<ProductValidationRequest> requests = List
                    .of(new ProductValidationRequest(productId, Quantity.of(10), null));
            CoreException ex = assertThrows(CoreException.class, () -> orderService.create(USER_ID, requests));
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @Test
        void create_whenRequestsEmpty_shouldThrowBadRequest() {
            CoreException ex = assertThrows(CoreException.class, () -> orderService.create(USER_ID, List.of()));
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("findById 시")
    @Nested
    class FindById {

        @Test
        void findById_whenOwner_shouldReturnPresent() {
            Long productId = saveProduct("상품", 10);
            OrderModel order = orderService.create(USER_ID,
                    List.of(new ProductValidationRequest(productId, Quantity.of(1), null)));

            Optional<OrderModel> result = orderService.findById(USER_ID, order.getId());

            assertThat(result).isPresent();
            assertThat(result.get().getId()).isEqualTo(order.getId());
        }

        @Test
        void findById_whenWrongUser_shouldReturnEmpty() {
            Long productId = saveProduct("상품", 10);
            OrderModel order = orderService.create(USER_ID,
                    List.of(new ProductValidationRequest(productId, Quantity.of(1), null)));

            Optional<OrderModel> result = orderService.findById(OTHER_USER_ID, order.getId());

            assertThat(result).isEmpty();
        }

        @Test
        void findById_whenNotExists_shouldReturnEmpty() {
            Optional<OrderModel> result = orderService.findById(USER_ID, 999_999L);
            assertThat(result).isEmpty();
        }
    }

    @DisplayName("findOrders 시")
    @Nested
    class FindOrders {

        @Test
        void findOrders_shouldReturnOrdersInPeriod() {
            Long productId = saveProduct("상품", 10);
            orderService.create(USER_ID, List.of(new ProductValidationRequest(productId, Quantity.of(1), null)));
            ZonedDateTime start = ZonedDateTime.now().minusMinutes(1);
            ZonedDateTime end = ZonedDateTime.now().plusMinutes(1);

            List<OrderModel> result = orderService.findOrders(USER_ID, start, end, 0, 10);

            assertThat(result).hasSize(1);
        }

        @Test
        void findOrders_whenDifferentUser_shouldNotReturnOthersOrders() {
            Long productId = saveProduct("상품", 10);
            orderService.create(USER_ID, List.of(new ProductValidationRequest(productId, Quantity.of(1), null)));
            ZonedDateTime start = ZonedDateTime.now().minusMinutes(1);
            ZonedDateTime end = ZonedDateTime.now().plusMinutes(1);

            List<OrderModel> result = orderService.findOrders(OTHER_USER_ID, start, end, 0, 10);

            assertThat(result).isEmpty();
        }
    }

    @DisplayName("completePayment 시")
    @Nested
    class CompletePayment {

        @Test
        void completePayment_whenConcurrent_shouldDecreaseStockOnce() throws Exception {
            Long productId = saveProduct("상품", 100);
            OrderModel order = orderService.create(USER_ID,
                    List.of(new ProductValidationRequest(productId, Quantity.of(1), null)));

            int parallelism = 2;
            CountDownLatch gate = new CountDownLatch(1);
            CountDownLatch finished = new CountDownLatch(parallelism);
            AtomicReference<Throwable> error = new AtomicReference<>();
            ExecutorService pool = Executors.newFixedThreadPool(parallelism);
            Long orderId = order.getId();
            for (int i = 0; i < parallelism; i++) {
                pool.submit(() -> {
                    try {
                        gate.await();
                        orderService.completePayment(orderId);
                    } catch (Throwable t) {
                        error.compareAndSet(null, t);
                    } finally {
                        finished.countDown();
                    }
                });
            }
            gate.countDown();
            assertThat(finished.await(60, TimeUnit.SECONDS)).isTrue();
            pool.shutdown();
            assertThat(error.get()).withFailMessage(() -> String.valueOf(error.get())).isNull();

            Optional<ProductModel> productAfter = productRepository.findById(productId);
            assertThat(productAfter).isPresent();
            assertThat(productAfter.get().getStockQuantity()).isEqualTo(99);

            Optional<OrderModel> orderAfter = orderRepository.findById(orderId);
            assertThat(orderAfter).isPresent();
            assertThat(orderAfter.get().getStatus()).isEqualTo(OrderStatus.PAID);
        }
    }

    @DisplayName("cancel 시")
    @Nested
    class Cancel {

        @Test
        void cancel_whenOrdered_shouldPersistCancelled() {
            Long productId = saveProduct("상품", 10);
            OrderModel order = orderService.create(USER_ID,
                    List.of(new ProductValidationRequest(productId, Quantity.of(1), null)));

            OrderModel cancelled = orderService.cancel(USER_ID, order.getId());

            assertThat(cancelled.getStatus()).isEqualTo(OrderStatus.CANCELLED);
            Optional<OrderModel> found = orderRepository.findById(order.getId());
            assertThat(found).isPresent();
            assertThat(found.get().getStatus()).isEqualTo(OrderStatus.CANCELLED);
        }

        @Test
        void cancel_whenWrongUser_shouldThrowNotFound() {
            Long productId = saveProduct("상품", 10);
            OrderModel order = orderService.create(USER_ID,
                    List.of(new ProductValidationRequest(productId, Quantity.of(1), null)));

            CoreException ex = assertThrows(CoreException.class,
                    () -> orderService.cancel(OTHER_USER_ID, order.getId()));
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }

        @Test
        void cancel_whenOrderNotExists_shouldThrowNotFound() {
            CoreException ex = assertThrows(CoreException.class, () -> orderService.cancel(USER_ID, 999_999L));
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }

        @Test
        void cancel_whenAlreadyCancelled_shouldThrowBadRequest() {
            Long productId = saveProduct("상품", 10);
            OrderModel order = orderService.create(USER_ID,
                    List.of(new ProductValidationRequest(productId, Quantity.of(1), null)));
            orderService.cancel(USER_ID, order.getId());

            CoreException ex = assertThrows(CoreException.class, () -> orderService.cancel(USER_ID, order.getId()));
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @Test
        void cancel_whenPaid_shouldRestoreStockThenPersistCancelled() {
            // given: 상품 재고 10, PAID 상태 주문(수량 2)을 직접 저장하고, 재고를 8로 줄여 결제 완료 상태를 시뮬레이션
            Long productId = saveProduct("상품", 10);
            ProductModel product = productRepository.findById(productId).orElseThrow();
            OrderModel order = OrderModel.withStatus(USER_ID, OrderStatus.PAID, ZonedDateTime.now());
            order.addItem(OrderItemModel.of(product.snapshotForOrder(), Quantity.of(2), null));
            order.validateHasItems();
            OrderModel savedOrder = orderRepository.save(order);
            product.updateStockQuantity(StockQuantity.of(8));
            productRepository.save(product);

            // when
            OrderModel cancelled = orderService.cancel(USER_ID, savedOrder.getId());

            // then: 주문 CANCELLED, 재고 10으로 복구
            assertThat(cancelled.getStatus()).isEqualTo(OrderStatus.CANCELLED);
            Optional<OrderModel> found = orderRepository.findById(savedOrder.getId());
            assertThat(found).isPresent();
            assertThat(found.get().getStatus()).isEqualTo(OrderStatus.CANCELLED);
            Optional<ProductModel> productAfter = productRepository.findById(productId);
            assertThat(productAfter).isPresent();
            assertThat(productAfter.get().getStockQuantity()).isEqualTo(10);
        }
    }
}
