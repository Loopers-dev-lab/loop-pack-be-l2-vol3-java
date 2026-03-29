package com.loopers.application.order;

import com.loopers.domain.coupon.CouponIssueRepository;
import com.loopers.domain.coupon.CouponRepository;
import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderService;
import com.loopers.domain.order.OrderItem;
import com.loopers.domain.order.OrderRepository;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.product.exception.ProductInsufficientStockException;
import com.loopers.domain.user.User;
import com.loopers.domain.user.UserRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class OrderFacadeTest {

    private OrderRepository orderRepository;
    private ProductRepository productRepository;
    private UserRepository userRepository;
    private CouponRepository couponRepository;
    private CouponIssueRepository couponIssueRepository;
    private ApplicationEventPublisher eventPublisher;
    private OrderFacade orderFacade;

    private final Long userId = 1L;
    private final Long productId = 10L;

    @BeforeEach
    void setUp() {
        orderRepository = mock(OrderRepository.class);
        productRepository = mock(ProductRepository.class);
        userRepository = mock(UserRepository.class);
        couponRepository = mock(CouponRepository.class);
        couponIssueRepository = mock(CouponIssueRepository.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        OrderService orderService = new OrderService();
        OrderPlacementTxService orderPlacementTxService = new OrderPlacementTxService(
            orderRepository,
            productRepository,
            couponRepository,
            couponIssueRepository,
            orderService,
            eventPublisher
        );
        orderFacade = new OrderFacade(
            orderRepository,
            productRepository,
            userRepository,
            couponRepository,
            couponIssueRepository,
            orderService,
            orderPlacementTxService,
            eventPublisher
        );
    }

    @DisplayName("placeOrder: 유효한 주문이면 주문을 생성하고 재고를 차감한다")
    @Test
    void placeOrder_success() {
        User user = mock(User.class);
        Product product = mock(Product.class);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(productRepository.findAllByIds(any())).thenReturn(List.of(product));
        when(product.getId()).thenReturn(productId);
        when(product.getName()).thenReturn("주문 상품");
        when(product.getPrice()).thenReturn(BigDecimal.valueOf(12000));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OrderDto.OrderInfo result = orderFacade.placeOrder(
            userId,
            List.of(new OrderDto.OrderLineCommand(productId, 2)),
            null
        );

        assertThat(result.userId()).isEqualTo(userId);
        assertThat(result.totalAmount()).isEqualByComparingTo("24000");
        assertThat(result.items()).hasSize(1);
        assertThat(result.items().getFirst().productName()).isEqualTo("주문 상품");
        verify(product).decreaseStock(2);
        verify(orderRepository).save(any(Order.class));
        verify(productRepository).save(product);
    }

    @DisplayName("placeOrder: 존재하지 않는 사용자면 예외가 발생한다")
    @Test
    void placeOrder_fail_userNotFound() {
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
            orderFacade.placeOrder(
                userId,
                List.of(new OrderDto.OrderLineCommand(productId, 1)),
                null
            )
        ).isInstanceOf(CoreException.class)
         .satisfies(e -> assertThat(((CoreException) e).getErrorType())
             .isEqualTo(ErrorType.USER_NOT_FOUND));

        verify(orderRepository, never()).save(any());
    }

    @DisplayName("placeOrder: 재고가 부족하면 예외가 발생한다")
    @Test
    void placeOrder_fail_insufficientStock() {
        User user = mock(User.class);
        Product product = mock(Product.class);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(productRepository.findAllByIds(any())).thenReturn(List.of(product));
        when(product.getId()).thenReturn(productId);
        when(product.getName()).thenReturn("신발");
        when(product.getPrice()).thenReturn(BigDecimal.valueOf(10000));
        doThrow(new ProductInsufficientStockException()).when(product).decreaseStock(1);

        assertThatThrownBy(() ->
            orderFacade.placeOrder(
                userId,
                List.of(new OrderDto.OrderLineCommand(productId, 1)),
                null
            )
        ).isInstanceOf(CoreException.class)
         .satisfies(e -> assertThat(((CoreException) e).getErrorType())
             .isEqualTo(ErrorType.PRODUCT_INSUFFICIENT_STOCK));

        verify(orderRepository, never()).save(any());
        verify(productRepository, never()).save(any());
    }

    @DisplayName("placeOrder: 존재하지 않는 상품이면 예외가 발생한다")
    @Test
    void placeOrder_fail_productNotFound() {
        User user = mock(User.class);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(productRepository.findAllByIds(any())).thenReturn(List.of());

        assertThatThrownBy(() ->
            orderFacade.placeOrder(
                userId,
                List.of(new OrderDto.OrderLineCommand(productId, 1)),
                null
            )
        ).isInstanceOf(CoreException.class)
         .satisfies(e -> assertThat(((CoreException) e).getErrorType())
             .isEqualTo(ErrorType.PRODUCT_NOT_FOUND));

        verify(orderRepository, never()).save(any());
    }

    @DisplayName("getOrder: 사용자의 주문을 조회한다")
    @Test
    void getOrder_success() {
        Order order = createOrder(userId, productId, 1, BigDecimal.valueOf(5000));
        when(orderRepository.findByIdAndUserId(100L, userId)).thenReturn(Optional.of(order));

        OrderDto.OrderInfo result = orderFacade.getOrder(userId, 100L);

        assertThat(result.userId()).isEqualTo(userId);
        assertThat(result.items()).hasSize(1);
        assertThat(result.totalAmount()).isEqualByComparingTo("5000");
    }

    @DisplayName("getOrder: 주문이 없으면 예외가 발생한다")
    @Test
    void getOrder_fail_notFound() {
        when(orderRepository.findByIdAndUserId(100L, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderFacade.getOrder(userId, 100L))
            .isInstanceOf(CoreException.class)
            .satisfies(e -> assertThat(((CoreException) e).getErrorType())
                .isEqualTo(ErrorType.ORDER_NOT_FOUND));
    }

    @DisplayName("getOrders: 기간 내 주문 목록을 조회한다")
    @Test
    void getOrders_success() {
        Order order1 = createOrder(userId, 10L, 1, BigDecimal.valueOf(1000));
        Order order2 = createOrder(userId, 11L, 2, BigDecimal.valueOf(2000));
        ZonedDateTime startAt = ZonedDateTime.now().minusDays(1);
        ZonedDateTime endAt = ZonedDateTime.now().plusDays(1);

        when(orderRepository.findByUserIdAndDateRange(userId, startAt, endAt)).thenReturn(List.of(order1, order2));

        List<OrderDto.OrderInfo> result = orderFacade.getOrders(userId, startAt, endAt);

        assertThat(result).hasSize(2);
    }

    @DisplayName("getAdminOrders: 관리자 주문 목록을 페이지로 조회한다")
    @Test
    void getAdminOrders_success() {
        PageRequest pageable = PageRequest.of(0, 20);
        Order order = createOrder(userId, productId, 1, BigDecimal.valueOf(1000));
        when(orderRepository.findAll(pageable)).thenReturn(new PageImpl<>(List.of(order)));

        Page<OrderDto.OrderInfo> result = orderFacade.getAdminOrders(pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    @DisplayName("cancelOrder: 주문 취소 시 재고를 복구하고 상태를 CANCELLED로 변경한다")
    @Test
    void cancelOrder_success() {
        Order order = createOrder(userId, productId, 2, BigDecimal.valueOf(7000));
        Product product = mock(Product.class);
        when(product.getId()).thenReturn(productId);

        when(orderRepository.findByIdAndUserId(100L, userId)).thenReturn(Optional.of(order));
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(orderRepository.save(order)).thenReturn(order);
        when(productRepository.save(product)).thenReturn(product);

        OrderDto.OrderInfo result = orderFacade.cancelOrder(userId, 100L);

        assertThat(result.status()).isEqualTo(com.loopers.domain.order.OrderStatus.CANCELLED);
        verify(product).increaseStock(2);
        verify(orderRepository).save(order);
        verify(productRepository).save(product);
    }

    @DisplayName("cancelOrder: 이미 취소된 주문이면 멱등하게 성공하고 재고 복구를 다시 수행하지 않는다")
    @Test
    void cancelOrder_idempotent_whenAlreadyCancelled() {
        Order order = createOrder(userId, productId, 1, BigDecimal.valueOf(5000));
        order.cancel();
        Product product = mock(Product.class);
        when(product.getId()).thenReturn(productId);

        when(orderRepository.findByIdAndUserId(100L, userId)).thenReturn(Optional.of(order));
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));

        OrderDto.OrderInfo result = orderFacade.cancelOrder(userId, 100L);

        assertThat(result.status()).isEqualTo(com.loopers.domain.order.OrderStatus.CANCELLED);
        verify(product, never()).increaseStock(anyInt());
        verify(orderRepository, never()).save(order);
    }

    @DisplayName("cancelOrder: 주문이 없으면 예외가 발생한다")
    @Test
    void cancelOrder_fail_notFound() {
        when(orderRepository.findByIdAndUserId(100L, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderFacade.cancelOrder(userId, 100L))
            .isInstanceOf(CoreException.class)
            .satisfies(e -> assertThat(((CoreException) e).getErrorType())
                .isEqualTo(ErrorType.ORDER_NOT_FOUND));
    }

    @DisplayName("cancelOrder: 주문 취소 시 OrderCancelledEvent를 발행한다")
    @Test
    void cancelOrder_publishesOrderCancelledEvent() {
        Order order = createOrder(userId, productId, 1, BigDecimal.valueOf(5000));
        Product product = mock(Product.class);
        when(product.getId()).thenReturn(productId);

        when(orderRepository.findByIdAndUserId(100L, userId)).thenReturn(Optional.of(order));
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(orderRepository.save(order)).thenReturn(order);
        when(productRepository.save(product)).thenReturn(product);

        orderFacade.cancelOrder(userId, 100L);

        verify(eventPublisher).publishEvent(any(com.loopers.application.order.event.OrderCancelledEvent.class));
    }

    @DisplayName("cancelOrder: 이미 취소된 주문이면 이벤트를 발행하지 않는다")
    @Test
    void cancelOrder_alreadyCancelled_noEvent() {
        Order order = createOrder(userId, productId, 1, BigDecimal.valueOf(5000));
        order.cancel();
        Product product = mock(Product.class);
        when(product.getId()).thenReturn(productId);

        when(orderRepository.findByIdAndUserId(100L, userId)).thenReturn(Optional.of(order));
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));

        orderFacade.cancelOrder(userId, 100L);

        verify(eventPublisher, never()).publishEvent(any());
    }

    private Order createOrder(Long userId, Long productId, Integer quantity, BigDecimal price) {
        OrderItem orderItem = OrderItem.createSnapshot(productId, "테스트상품-" + productId, price, quantity);
        return Order.create(userId, List.of(orderItem));
    }
}
