package com.loopers.application.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.loopers.application.brand.BrandService;
import com.loopers.application.order.Cart.CartItem;
import com.loopers.application.product.ProductCommand;
import com.loopers.application.product.ProductService;
import com.loopers.domain.order.OrderRepository;
import com.loopers.domain.order.OrderStatus;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.shared.Money;
import com.loopers.support.page.PageSize;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.utils.DatabaseCleanUp;

@SpringBootTest
class OrderServiceIntegrationTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private ProductService productService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private BrandService brandService;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("주문을 생성할 때,")
    @Nested
    class CreateOrder {

        @DisplayName("유효한 요청이면, 주문이 DB에 저장되고 주문 ID가 반환된다.")
        @Test
        void savesOrderToDatabase_whenValidRequest() {
            // arrange
            var productId = createBrandAndProduct("테스트 상품", 10000L, 100L);
            var cart = new Cart(
                    1L,
                    List.of(new CartItem(productId, 2L))
            );

            // act
            var orderId = orderService.createOrder(cart);

            // assert
            var savedOrder = orderRepository.findById(orderId).orElseThrow();
            assertAll(
                    () -> assertThat(savedOrder.getUserId()).isEqualTo(1L),
                    () -> assertThat(savedOrder.getName()).isEqualTo("테스트 상품"),
                    () -> assertThat(savedOrder.getStatus()).isEqualTo(OrderStatus.CREATED),
                    () -> assertThat(savedOrder.getTotalPrice()).isEqualTo(Money.wons(20000L)),
                    () -> assertThat(savedOrder.getOrderedAt()).isNotNull()
            );
        }

        @DisplayName("주문 시 상품의 재고가 차감된다.")
        @Test
        void deductsProductStock_whenOrderCreated() {
            // arrange
            var productId = createBrandAndProduct("테스트 상품", 10000L, 100L);
            var cart = new Cart(
                    1L,
                    List.of(new CartItem(productId, 30L))
            );

            // act
            orderService.createOrder(cart);

            // assert
            var product = productRepository.findById(productId).orElseThrow();
            assertThat(product.getStock().getValue()).isEqualTo(70L);
        }

        @DisplayName("존재하지 않는 상품이 포함되면, PRODUCT_NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsException_whenProductNotFound() {
            // arrange
            var cart = new Cart(
                    1L,
                    List.of(new CartItem(999L, 1L))
            );

            // act & assert
            assertThatThrownBy(() -> orderService.createOrder(cart))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.PRODUCT_NOT_FOUND));
        }

        @DisplayName("삭제된 상품이 포함되면, PRODUCT_NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsException_whenProductIsDeleted() {
            // arrange
            var productId = createBrandAndProduct("테스트 상품", 10000L, 100L);
            productService.deleteProduct(productId);

            var cart = new Cart(
                    1L,
                    List.of(new CartItem(productId, 1L))
            );

            // act & assert
            assertThatThrownBy(() -> orderService.createOrder(cart))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.PRODUCT_NOT_FOUND));
        }

        @DisplayName("같은 상품이 중복 포함되면, DUPLICATE_ORDER_PRODUCT 예외가 발생한다.")
        @Test
        void throwsException_whenDuplicateProductIncluded() {
            // arrange
            var productId = createBrandAndProduct("테스트 상품", 10000L, 100L);
            var cart = new Cart(
                    1L,
                    List.of(
                            new CartItem(productId, 1L),
                            new CartItem(productId, 2L)
                    )
            );

            // act & assert
            assertThatThrownBy(() -> orderService.createOrder(cart))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.DUPLICATE_ORDER_PRODUCT));
        }

        @DisplayName("품절된 상품을 주문하면, SOLD_OUT_PRODUCT 예외가 발생한다.")
        @Test
        void throwsException_whenProductSoldOut() {
            // arrange
            var productId = createBrandAndProduct("품절 상품", 10000L, 3L);
            orderService.createOrder(new Cart(
                    1L,
                    List.of(new CartItem(productId, 3L))
            ));

            var cart = new Cart(
                    1L,
                    List.of(new CartItem(productId, 1L))
            );

            // act & assert
            assertThatThrownBy(() -> orderService.createOrder(cart))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.SOLD_OUT_PRODUCT));
        }

        @DisplayName("재고보다 많은 수량을 주문하면, INSUFFICIENT_STOCK 예외가 발생한다.")
        @Test
        void throwsException_whenInsufficientStock() {
            // arrange
            var productId = createBrandAndProduct("테스트 상품", 10000L, 5L);
            var cart = new Cart(
                    1L,
                    List.of(new CartItem(productId, 10L))
            );

            // act & assert
            assertThatThrownBy(() -> orderService.createOrder(cart))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.INSUFFICIENT_STOCK));
        }

        @DisplayName("동시에 같은 상품을 주문하면, 재고만큼만 성공하고 나머지는 실패한다.")
        @Test
        void onlyStockAmountSucceeds_whenConcurrentOrdersExceedStock() throws InterruptedException {
            // arrange
            long stock = 5L;
            int threadCount = 10;
            var productId = createBrandAndProduct("상품", 10000L, stock);
            ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
            CountDownLatch latch = new CountDownLatch(threadCount);
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger failCount = new AtomicInteger(0);

            // act
            for (int i = 0; i < threadCount; i++) {
                long userId = i + 1;
                executorService.execute(() -> {
                    try {
                        orderService.createOrder(new Cart(
                                userId,
                                List.of(new CartItem(productId, 1L))
                        ));
                        successCount.incrementAndGet();
                    } catch (Exception e) {
                        failCount.incrementAndGet();
                    } finally {
                        latch.countDown();
                    }
                });
            }
            latch.await();
            executorService.shutdown();

            // assert
            var product = productRepository.findById(productId).orElseThrow();
            assertAll(
                    () -> assertThat(successCount.get() + failCount.get()).isEqualTo(threadCount),
                    () -> assertThat(successCount.get()).isEqualTo((int) stock),
                    () -> assertThat(product.getStock().getValue()).isZero()
            );
        }
    }

    @DisplayName("주문 목록을 조회할 때,")
    @Nested
    class GetOrders {

        @DisplayName("날짜 범위 내 주문이 존재하면, 주문 목록이 반환된다.")
        @Test
        void returnsOrderPage_whenOrdersExistInDateRange() {
            // arrange
            var productId = createBrandAndProduct("테스트 상품", 10000L, 100L);
            orderService.createOrder(new Cart(1L, List.of(new CartItem(productId, 1L))));

            var today = LocalDate.now();
            var pageSize = new PageSize(0, 20);

            // act
            var result = orderService.getOrders(1L, today, today, pageSize);

            // assert
            assertAll(
                    () -> assertThat(result.content()).hasSize(1),
                    () -> assertThat(result.content().get(0).name()).isEqualTo("테스트 상품"),
                    () -> assertThat(result.content().get(0).status()).isEqualTo(OrderStatus.CREATED),
                    () -> assertThat(result.hasNext()).isFalse()
            );
        }

        @DisplayName("주문이 없으면, 빈 페이지를 반환한다.")
        @Test
        void returnsEmptyPage_whenNoOrdersExist() {
            // arrange
            var today = LocalDate.now();
            var pageSize = new PageSize(0, 20);

            // act
            var result = orderService.getOrders(1L, today, today, pageSize);

            // assert
            assertAll(
                    () -> assertThat(result.content()).isEmpty(),
                    () -> assertThat(result.hasNext()).isFalse()
            );
        }

        @DisplayName("본인의 주문만 반환된다.")
        @Test
        void returnsOnlyOwnOrders_whenOtherUserOrdersExist() {
            // arrange
            var productId = createBrandAndProduct("테스트 상품", 10000L, 100L);
            orderService.createOrder(new Cart(1L, List.of(new CartItem(productId, 1L))));
            orderService.createOrder(new Cart(2L, List.of(new CartItem(productId, 1L))));

            var today = LocalDate.now();
            var pageSize = new PageSize(0, 20);

            // act
            var result = orderService.getOrders(1L, today, today, pageSize);

            // assert
            assertThat(result.content()).hasSize(1);
        }

        @DisplayName("날짜 범위 밖의 주문은 조회되지 않는다.")
        @Test
        void excludesOrdersOutsideDateRange() {
            // arrange
            var productId = createBrandAndProduct("테스트 상품", 10000L, 100L);
            orderService.createOrder(new Cart(1L, List.of(new CartItem(productId, 1L))));

            var pastDate = LocalDate.of(2020, 1, 1);
            var pageSize = new PageSize(0, 20);

            // act
            var result = orderService.getOrders(1L, pastDate, pastDate, pageSize);

            // assert
            assertAll(
                    () -> assertThat(result.content()).isEmpty(),
                    () -> assertThat(result.hasNext()).isFalse()
            );
        }
    }

    private Long createBrandAndProduct(String productName, Long price, Long stock) {
        var brand = brandService.createBrand("테스트 브랜드", "https://example.com/logo.png", null);
        return productService.createProduct(new ProductCommand.CreateProductCommand(
                brand.id(), productName, "https://example.com/thumb.png", price, stock, null
        ));
    }
}
