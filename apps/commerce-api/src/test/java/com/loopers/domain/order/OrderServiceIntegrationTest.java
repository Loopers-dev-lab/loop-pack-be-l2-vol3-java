package com.loopers.domain.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.loopers.domain.order.Cart.CartItem;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.shared.Money;
import com.loopers.support.BaseIntegrationTest;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

class OrderServiceIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private ProductRepository productRepository;

    private Long brandId;

    @BeforeEach
    void setUp() {
        brandId = initDefaultBrand();
    }

    @DisplayName("주문을 생성할 때,")
    @Nested
    class Create {

        @DisplayName("유효한 상품과 수량이면, 주문이 DB에 저장된다.")
        @Test
        void savesOrderToDatabase_whenValidProductAndQuantity() {
            // arrange
            var productId = createProduct(brandId, "테스트 상품", 10000L, 100L);
            Product product = productRepository.findById(productId).orElseThrow();
            Cart cart = createCart(1L, product, 2L);

            // act
            Order result = orderService.create(cart, Money.ZERO, null);

            // assert
            var savedOrder = orderRepository.findByIdWithItems(result.getId()).orElseThrow();
            assertAll(
                    () -> assertThat(savedOrder.getUserId()).isEqualTo(1L),
                    () -> assertThat(savedOrder.getStatus()).isEqualTo(OrderStatus.ORDERED),
                    () -> assertThat(savedOrder.getOrderItems()).hasSize(1),
                    () -> assertThat(savedOrder.getTotalPrice().getAmount()).isEqualTo(20000L)
            );
        }

        @DisplayName("여러 상품을 주문하면, 모든 주문 항목이 저장된다.")
        @Test
        void savesAllOrderItems_whenMultipleProducts() {
            // arrange
            var productId1 = createProduct(brandId, "상품 1", 10000L, 100L);
            var productId2 = createProduct(brandId, "상품 2", 20000L, 100L);
            Product product1 = productRepository.findById(productId1).orElseThrow();
            Product product2 = productRepository.findById(productId2).orElseThrow();
            Cart cart = new Cart(1L, List.of(
                    new CartItem(product1.getId(), product1.getName().getValue(),
                            product1.getThumbnailUrl().getValue(), product1.getPrice(), 1L),
                    new CartItem(product2.getId(), product2.getName().getValue(),
                            product2.getThumbnailUrl().getValue(), product2.getPrice(), 2L)
            ));

            // act
            Order result = orderService.create(cart, Money.ZERO, null);

            // assert
            var savedOrder = orderRepository.findByIdWithItems(result.getId()).orElseThrow();
            assertAll(
                    () -> assertThat(savedOrder.getOrderItems()).hasSize(2),
                    () -> assertThat(savedOrder.getTotalPrice().getAmount()).isEqualTo(50000L)
            );
        }
    }

    @DisplayName("ID로 주문을 조회할 때,")
    @Nested
    class GetById {

        @DisplayName("존재하는 주문이면, 주문 항목을 포함하여 반환한다.")
        @Test
        void returnsOrderWithItems_whenOrderExists() {
            // arrange
            var productId = createProduct(brandId, "테스트 상품", 10000L, 100L);
            Product product = productRepository.findById(productId).orElseThrow();
            Order created = orderService.create(createCart(1L, product, 2L), Money.ZERO, null);

            // act
            Order result = orderService.getById(created.getId());

            // assert
            assertAll(
                    () -> assertThat(result.getId()).isEqualTo(created.getId()),
                    () -> assertThat(result.getUserId()).isEqualTo(1L),
                    () -> assertThat(result.getOrderItems()).hasSize(1)
            );
        }

        @DisplayName("존재하지 않는 주문이면, ORDER_NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsException_whenOrderNotFound() {
            assertThatThrownBy(() -> orderService.getById(999L))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.ORDER_NOT_FOUND));
        }
    }

    @DisplayName("내 주문을 조회할 때,")
    @Nested
    class GetMyOrder {

        @DisplayName("본인의 주문이면, 주문 정보가 반환된다.")
        @Test
        void returnsOrder_whenOwnerRequests() {
            // arrange
            var productId = createProduct(brandId, "테스트 상품", 10000L, 100L);
            Product product = productRepository.findById(productId).orElseThrow();
            Order created = orderService.create(createCart(1L, product, 2L), Money.ZERO, null);

            // act
            Order result = orderService.getMyOrder(1L, created.getOrderKey());

            // assert
            assertAll(
                    () -> assertThat(result.getId()).isEqualTo(created.getId()),
                    () -> assertThat(result.getUserId()).isEqualTo(1L),
                    () -> assertThat(result.getStatus()).isEqualTo(OrderStatus.ORDERED),
                    () -> assertThat(result.getOrderItems()).hasSize(1)
            );
        }

        @DisplayName("존재하지 않는 주문이면, ORDER_NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsException_whenOrderNotFound() {
            // act & assert
            assertThatThrownBy(() -> orderService.getMyOrder(1L, "non-existent-key"))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.ORDER_NOT_FOUND));
        }

        @DisplayName("다른 사용자의 주문이면, FORBIDDEN_ORDER_ACCESS 예외가 발생한다.")
        @Test
        void throwsException_whenNotOwner() {
            // arrange
            var productId = createProduct(brandId, "테스트 상품", 10000L, 100L);
            Product product = productRepository.findById(productId).orElseThrow();
            Order created = orderService.create(createCart(1L, product, 1L), Money.ZERO, null);

            // act & assert
            assertThatThrownBy(() -> orderService.getMyOrder(999L, created.getOrderKey()))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.FORBIDDEN_ORDER_ACCESS));
        }
    }

    @DisplayName("주문을 결제 완료할 때,")
    @Nested
    class Pay {

        @DisplayName("CREATED 상태의 주문이면, PAID로 변경된다.")
        @Test
        void changesStatusToPaid_whenCreated() {
            // arrange
            var productId = createProduct(brandId, "테스트 상품", 10000L, 100L);
            Product product = productRepository.findById(productId).orElseThrow();
            Order created = orderService.create(createCart(1L, product, 1L), Money.ZERO, null);

            // act
            Order result = orderService.pay(created.getId());

            // assert
            assertThat(result.getStatus()).isEqualTo(OrderStatus.PAID);
        }

        @DisplayName("존재하지 않는 주문이면, ORDER_NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsException_whenOrderNotFound() {
            assertThatThrownBy(() -> orderService.pay(999L))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.ORDER_NOT_FOUND));
        }

        @DisplayName("이미 PAID 상태이면, ORDER_NOT_PAYABLE 예외가 발생한다.")
        @Test
        void throwsException_whenAlreadyPaid() {
            // arrange
            var productId = createProduct(brandId, "테스트 상품", 10000L, 100L);
            Product product = productRepository.findById(productId).orElseThrow();
            Order created = orderService.create(createCart(1L, product, 1L), Money.ZERO, null);
            orderService.pay(created.getId());

            // act & assert
            assertThatThrownBy(() -> orderService.pay(created.getId()))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.ORDER_NOT_PAYABLE));
        }
    }

    @DisplayName("주문을 실패 처리할 때,")
    @Nested
    class Fail {

        @DisplayName("CREATED 상태의 주문이면, FAILED로 변경된다.")
        @Test
        void changesStatusToFailed_whenCreated() {
            // arrange
            var productId = createProduct(brandId, "테스트 상품", 10000L, 100L);
            Product product = productRepository.findById(productId).orElseThrow();
            Order created = orderService.create(createCart(1L, product, 1L), Money.ZERO, null);

            // act
            Order result = orderService.fail(created.getId());

            // assert
            assertAll(
                    () -> assertThat(result.getStatus()).isEqualTo(OrderStatus.FAILED),
                    () -> assertThat(result.getOrderItems()).hasSize(1)
            );
        }

        @DisplayName("존재하지 않는 주문이면, ORDER_NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsException_whenOrderNotFound() {
            assertThatThrownBy(() -> orderService.fail(999L))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.ORDER_NOT_FOUND));
        }

        @DisplayName("이미 PAID 상태이면, ORDER_NOT_FAILABLE 예외가 발생한다.")
        @Test
        void throwsException_whenAlreadyPaid() {
            // arrange
            var productId = createProduct(brandId, "테스트 상품", 10000L, 100L);
            Product product = productRepository.findById(productId).orElseThrow();
            Order created = orderService.create(createCart(1L, product, 1L), Money.ZERO, null);
            orderService.pay(created.getId());

            // act & assert
            assertThatThrownBy(() -> orderService.fail(created.getId()))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.ORDER_NOT_FAILABLE));
        }
    }

    private Cart createCart(Long userId, Product product, Long quantity) {
        List<Cart.CartItem> items = List.of(new Cart.CartItem(
                product.getId(),
                product.getName().getValue(),
                product.getThumbnailUrl().getValue(),
                product.getPrice(),
                quantity
        ));
        return new Cart(userId, items);
    }
}
