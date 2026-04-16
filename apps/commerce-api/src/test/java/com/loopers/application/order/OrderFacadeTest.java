package com.loopers.application.order;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.coupon.CouponTemplateRepository;
import com.loopers.domain.coupon.IssuedCouponRepository;
import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderCreatedEvent;
import com.loopers.domain.order.OrderService;
import com.loopers.domain.outbox.OutboxEventPublisher;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.user.User;
import com.loopers.domain.user.UserService;
import com.loopers.infrastructure.preorder.PreOrderCacheService;
import com.loopers.infrastructure.queue.QueueCacheService;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OrderFacadeTest {

    @Mock
    private OrderService orderService;

    @Mock
    private ProductService productService;

    @Mock
    private UserService userService;

    @Mock
    private BrandService brandService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private OutboxEventPublisher outboxEventPublisher;

    @Mock
    private QueueCacheService queueCacheService;

    @Mock
    private IssuedCouponRepository issuedCouponRepository;

    @Mock
    private CouponTemplateRepository couponTemplateRepository;

    @Mock
    private PreOrderCacheService preOrderCacheService;

    private OrderFacade orderFacade;

    @BeforeEach
    void setUp() {
        orderFacade = new OrderFacade(orderService, productService, userService, brandService,
            eventPublisher, outboxEventPublisher, queueCacheService,
            issuedCouponRepository, couponTemplateRepository, preOrderCacheService);
    }

    @DisplayName("주문 생성")
    @Nested
    class CreateOrder {

        @DisplayName("주문 생성 시 OrderCreatedEvent가 발행된다.")
        @Test
        void publishesOrderCreatedEvent_whenOrderCreated() {
            // Arrange
            String loginId = "testuser";
            String rawPassword = "Test1234!";
            User user = new User(loginId, "encrypted", "홍길동", "19900101", "test@example.com");
            user.restoreBalance(100000L);
            Product product = new Product(0L, "테스트상품", 10000, 100, "설명", "img.jpg");
            Brand brand = new Brand("테스트브랜드", "설명");
            Order order = new Order(user.getId(), "ORD-20240101-TEST", 10000L);
            List<OrderRequest.OrderItemRequest> items = List.of(new OrderRequest.OrderItemRequest(1L, 1));

            given(userService.authenticate(loginId, rawPassword)).willReturn(user);
            given(queueCacheService.validateToken(any(), any())).willReturn(true);
            given(productService.getProduct(1L)).willReturn(product);
            given(brandService.getBrandsByIds(any())).willReturn(List.of(brand));
            given(orderService.generateOrderNumber()).willReturn("ORD-20240101-TEST");
            given(orderService.createOrder(any(), any(), any())).willReturn(order);
            given(orderService.createOrderItem(any(), any(), any(), any(), any(), any(), any())).willReturn(null);
            given(orderService.getOrderItems(any())).willReturn(List.of());

            // Act
            orderFacade.createOrder(loginId, rawPassword, "MOCK_TOKEN", items, null);

            // Assert
            verify(eventPublisher).publishEvent(any(OrderCreatedEvent.class));
        }
    }

    @DisplayName("주문 상세 조회")
    @Nested
    class GetOrderDetail {

        @DisplayName("본인 주문을 조회하면, 주문 정보를 반환한다.")
        @Test
        void returnsOrderInfo_whenUserOwnsTheOrder() {
            // Arrange
            String loginId = "testuser";
            String rawPassword = "Test1234!";
            User user = new User(loginId, "encrypted", "홍길동", "19900101", "test@example.com");
            // user.getId() == 0L (BaseEntity 기본값), 동일한 userId로 주문 생성
            Order order = new Order(user.getId(), "ORD-20240101-ABCD1234", 50000L);

            given(userService.authenticate(loginId, rawPassword)).willReturn(user);
            given(orderService.getOrder(1L)).willReturn(order);
            given(orderService.getOrderItems(1L)).willReturn(List.of());

            // Act
            OrderInfo result = orderFacade.getOrderDetail(loginId, rawPassword, 1L);

            // Assert
            assertThat(result.orderNumber()).isEqualTo("ORD-20240101-ABCD1234");
            assertThat(result.totalAmount()).isEqualTo(50000L);
        }

        @DisplayName("다른 사용자의 주문을 조회하면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenUserDoesNotOwnTheOrder() {
            // Arrange
            String loginId = "testuser";
            String rawPassword = "Test1234!";
            User user = new User(loginId, "encrypted", "홍길동", "19900101", "test@example.com");
            // user.getId() == 0L, 다른 사용자(99L) 소유의 주문
            Order order = new Order(99L, "ORD-20240101-ABCD9999", 50000L);

            given(userService.authenticate(loginId, rawPassword)).willReturn(user);
            given(orderService.getOrder(1L)).willReturn(order);

            // Act
            CoreException exception = assertThrows(CoreException.class, () ->
                orderFacade.getOrderDetail(loginId, rawPassword, 1L)
            );

            // Assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("주문 승인")
    @Nested
    class ApproveOrder {

        @DisplayName("인증에 성공하면, 승인된 주문 정보를 반환한다.")
        @Test
        void returnsApprovedOrderInfo_whenAuthenticated() {
            // Arrange
            String loginId = "testuser";
            String rawPassword = "Test1234!";
            User user = new User(loginId, "encrypted", "홍길동", "19900101", "test@example.com");
            // user.getId() == 0L (BaseEntity 기본값)
            Order order = new Order(0L, "ORD-20240101-ABCD1234", 50000L);

            given(userService.authenticate(loginId, rawPassword)).willReturn(user);
            given(orderService.approveOrder(1L)).willReturn(order);
            given(orderService.getOrderItems(1L)).willReturn(List.of());

            // Act
            OrderInfo result = orderFacade.approveOrder(loginId, rawPassword, 1L);

            // Assert
            assertThat(result.orderNumber()).isEqualTo("ORD-20240101-ABCD1234");
        }

        @DisplayName("인증에 실패하면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenAuthenticationFails() {
            // Arrange
            given(userService.authenticate("nouser", "Test1234!"))
                .willThrow(new CoreException(ErrorType.NOT_FOUND, "회원을 찾을 수 없습니다."));

            // Act
            CoreException exception = assertThrows(CoreException.class, () ->
                orderFacade.approveOrder("nouser", "Test1234!", 1L)
            );

            // Assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }
}
