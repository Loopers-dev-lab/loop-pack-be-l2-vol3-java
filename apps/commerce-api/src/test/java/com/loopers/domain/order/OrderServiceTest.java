package com.loopers.domain.order;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.instancio.Instancio;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.instancio.Select.field;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderService 단위 테스트")
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderHistoryService orderHistoryService;

    @InjectMocks
    private OrderService orderService;

    @Nested
    @DisplayName("주문 생성")
    class CreateOrder {

        @Test
        @DisplayName("성공: 유효한 주문 요청으로 주문을 생성한다")
        void createOrder_Success() {
            // Given
            Long userId = 1L;
            List<OrderItem> orderItems = List.of(
                    OrderItem.create(10L, "상품A", new BigDecimal("10000"), 2),
                    OrderItem.create(20L, "상품B", new BigDecimal("20000"), 1)
            );
            BigDecimal discountAmount = BigDecimal.ZERO;

            given(orderRepository.save(any(Order.class))).willAnswer(invocation -> invocation.getArgument(0));

            // When
            Order result = orderService.createOrder(userId, orderItems, discountAmount, null);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getUserId()).isEqualTo(userId);
            assertThat(result.getStatus()).isEqualTo(OrderStatus.CREATED);
            assertThat(result.getOrderItems()).hasSize(2);
            assertThat(result.getOriginalAmount()).isEqualByComparingTo(new BigDecimal("40000"));
            assertThat(result.getDiscountAmount()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(result.getTotalAmount()).isEqualByComparingTo(new BigDecimal("40000"));

            then(orderRepository).should().save(any(Order.class));
            then(orderHistoryService).should().recordHistory(any(), eq(null), eq(OrderStatus.CREATED), eq("주문 생성"));
        }

        @Test
        @DisplayName("성공: 단일 상품 주문을 생성한다")
        void createOrder_SingleItem() {
            // Given
            Long userId = 1L;
            List<OrderItem> orderItems = List.of(
                    OrderItem.create(10L, "상품A", new BigDecimal("15000"), 3)
            );
            BigDecimal discountAmount = BigDecimal.ZERO;

            given(orderRepository.save(any(Order.class))).willAnswer(invocation -> invocation.getArgument(0));

            // When
            Order result = orderService.createOrder(userId, orderItems, discountAmount, null);

            // Then
            assertThat(result.getUserId()).isEqualTo(userId);
            assertThat(result.getOrderItems()).hasSize(1);
            assertThat(result.getTotalAmount()).isEqualByComparingTo(new BigDecimal("45000"));
        }

        @Test
        @DisplayName("실패: userId가 null이면 BAD_REQUEST 예외를 던진다")
        void createOrder_NullUserId() {
            // Given
            List<OrderItem> orderItems = List.of(
                    OrderItem.create(10L, "상품A", new BigDecimal("10000"), 1)
            );
            BigDecimal discountAmount = BigDecimal.ZERO;

            // When & Then
            assertThatThrownBy(() -> orderService.createOrder(null, orderItems, discountAmount, null))
                    .isInstanceOf(CoreException.class)
                    .hasFieldOrPropertyWithValue("errorType", ErrorType.BAD_REQUEST)
                    .hasMessage("사용자 ID는 필수입니다.");
        }

        @Test
        @DisplayName("실패: 주문 항목이 비어있으면 BAD_REQUEST 예외를 던진다")
        void createOrder_EmptyItems() {
            // Given
            Long userId = 1L;
            List<OrderItem> orderItems = List.of();
            BigDecimal discountAmount = BigDecimal.ZERO;

            // When & Then
            assertThatThrownBy(() -> orderService.createOrder(userId, orderItems, discountAmount, null))
                    .isInstanceOf(CoreException.class)
                    .hasFieldOrPropertyWithValue("errorType", ErrorType.BAD_REQUEST)
                    .hasMessage("주문 상품이 없습니다.");
        }
    }

    @Nested
    @DisplayName("주문 조회")
    class GetById {

        @Test
        @DisplayName("성공: 유효한 주문 ID로 조회한다")
        void getById_Success() {
            // Given
            Long orderId = 1L;
            Order order = Instancio.of(Order.class)
                    .set(field(Order::getId), orderId)
                    .set(field(Order::getUserId), 1L)
                    .create();

            given(orderRepository.findActiveById(orderId)).willReturn(Optional.of(order));

            // When
            Order result = orderService.getById(orderId);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(orderId);
            then(orderRepository).should().findActiveById(orderId);
        }

        @Test
        @DisplayName("실패: 존재하지 않는 주문 ID로 조회하면 NOT_FOUND 예외를 던진다")
        void getById_NotFound() {
            // Given
            Long orderId = 999L;
            given(orderRepository.findActiveById(orderId)).willReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> orderService.getById(orderId))
                    .isInstanceOf(CoreException.class)
                    .hasFieldOrPropertyWithValue("errorType", ErrorType.NOT_FOUND)
                    .hasMessage("주문을 찾을 수 없습니다.");
        }
    }

    @Nested
    @DisplayName("사용자별 주문 목록 조회")
    class GetOrdersByUserId {

        @Test
        @DisplayName("성공: 사용자의 주문 목록을 조회한다")
        void getOrdersByUserId_Success() {
            // Given
            Long userId = 1L;
            Pageable pageable = PageRequest.of(0, 10);
            Order order = Instancio.of(Order.class)
                    .set(field(Order::getUserId), userId)
                    .create();
            Page<Order> page = new PageImpl<>(List.of(order), pageable, 1);

            given(orderRepository.findAllActiveByUserId(userId, pageable)).willReturn(page);

            // When
            Page<Order> result = orderService.getOrdersByUserId(userId, pageable);

            // Then
            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getUserId()).isEqualTo(userId);
            then(orderRepository).should().findAllActiveByUserId(userId, pageable);
        }
    }

    @Nested
    @DisplayName("전체 주문 목록 조회")
    class GetAllOrders {

        @Test
        @DisplayName("성공: 전체 주문 목록을 조회한다")
        void getAllOrders_Success() {
            // Given
            Pageable pageable = PageRequest.of(0, 10);
            Order order = Instancio.create(Order.class);
            Page<Order> page = new PageImpl<>(List.of(order), pageable, 1);

            given(orderRepository.findAllActive(pageable)).willReturn(page);

            // When
            Page<Order> result = orderService.getAllOrders(pageable);

            // Then
            assertThat(result.getContent()).hasSize(1);
            then(orderRepository).should().findAllActive(pageable);
        }
    }
}
