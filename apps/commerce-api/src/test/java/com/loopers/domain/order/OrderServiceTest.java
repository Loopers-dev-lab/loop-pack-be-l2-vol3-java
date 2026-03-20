package com.loopers.domain.order;

import com.loopers.support.enums.OrderStatus;
import com.loopers.support.enums.OrderType;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderService 도메인 서비스 테스트")
class OrderServiceTest {

    @Mock OrderRepository orderRepository;
    @Mock OrderItemRepository orderItemRepository;
    @Mock OrderCartRestoreRepository orderCartRestoreRepository;

    @InjectMocks
    OrderService orderService;

    // === 검증 및 병합 ===

    @Nested
    @DisplayName("검증 및 병합 (validateAndPrepare)")
    class ValidateAndPrepareTests {

        @Test
        @DisplayName("빈 항목으로 주문 시 ORDER_ITEM_EMPTY 예외가 발생한다")
        void validateAndPrepare_EmptyItems_ShouldThrow() {
            assertThatThrownBy(() -> orderService.validateAndPrepare(1L, List.of()))
                    .isInstanceOf(CoreException.class)
                    .satisfies(ex -> assertThat(((CoreException) ex).getErrorType())
                            .isEqualTo(ErrorType.ORDER_ITEM_EMPTY));
        }

        @Test
        @DisplayName("null 항목으로 주문 시 ORDER_ITEM_EMPTY 예외가 발생한다")
        void validateAndPrepare_NullItems_ShouldThrow() {
            assertThatThrownBy(() -> orderService.validateAndPrepare(1L, null))
                    .isInstanceOf(CoreException.class)
                    .satisfies(ex -> assertThat(((CoreException) ex).getErrorType())
                            .isEqualTo(ErrorType.ORDER_ITEM_EMPTY));
        }

        @Test
        @DisplayName("PENDING 주문 3건 이상일 때 ORDER_PENDING_LIMIT_EXCEEDED 예외가 발생한다")
        void validateAndPrepare_ExceedPendingLimit_ShouldThrow() {
            when(orderRepository.countByUserIdAndStatus(1L, OrderStatus.PENDING_PAYMENT)).thenReturn(3L);

            assertThatThrownBy(() -> orderService.validateAndPrepare(1L,
                    List.of(new OrderItemCommand(1L, 1))))
                    .isInstanceOf(CoreException.class)
                    .satisfies(ex -> assertThat(((CoreException) ex).getErrorType())
                            .isEqualTo(ErrorType.ORDER_PENDING_LIMIT_EXCEEDED));
        }

        @Test
        @DisplayName("동일 productId가 중복 전달되면 수량을 합산한다")
        void validateAndPrepare_DuplicateProductId_ShouldMergeQuantity() {
            when(orderRepository.countByUserIdAndStatus(1L, OrderStatus.PENDING_PAYMENT)).thenReturn(0L);

            List<OrderItemCommand> result = orderService.validateAndPrepare(1L, List.of(
                    new OrderItemCommand(1L, 2),
                    new OrderItemCommand(1L, 3)));

            assertThat(result).hasSize(1);
            assertThat(result.get(0).quantity()).isEqualTo(5);
        }

        @Test
        @DisplayName("결과가 productId 오름차순으로 정렬된다")
        void validateAndPrepare_ShouldSortByProductIdAsc() {
            when(orderRepository.countByUserIdAndStatus(1L, OrderStatus.PENDING_PAYMENT)).thenReturn(0L);

            List<OrderItemCommand> result = orderService.validateAndPrepare(1L, List.of(
                    new OrderItemCommand(3L, 1),
                    new OrderItemCommand(1L, 1)));

            assertThat(result).extracting(OrderItemCommand::productId)
                    .containsExactly(1L, 3L);
        }
    }

    // === 주문 생성 ===

    @Nested
    @DisplayName("주문 생성 (createOrder)")
    class CreateOrderTests {

        @Test
        @DisplayName("주문과 주문 항목이 저장되고 OrderModel이 반환된다")
        void createOrder_ShouldSaveOrderAndItems_ReturnOrderModel() {
            when(orderRepository.save(any(OrderModel.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));
            when(orderItemRepository.saveAll(anyList()))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            List<OrderItemSnapshot> snapshots = List.of(
                    new OrderItemSnapshot(1L, 2, "테스트상품",
                            BigDecimal.valueOf(10000), "brand-id", "테스트브랜드", null,
                            BigDecimal.valueOf(20000), BigDecimal.ZERO, BigDecimal.valueOf(20000)));

            OrderModel result = orderService.createOrder(1L, OrderType.DIRECT,
                    BigDecimal.valueOf(20000), snapshots);

            assertThat(result).isNotNull();
            verify(orderRepository).save(any(OrderModel.class));
            verify(orderItemRepository).saveAll(anyList());
        }

        @Test
        @DisplayName("orderType이 전달한 값으로 설정된다")
        void createOrder_ShouldSetOrderType() {
            when(orderRepository.save(any(OrderModel.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));
            when(orderItemRepository.saveAll(anyList()))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            orderService.createOrder(1L, OrderType.CART,
                    BigDecimal.valueOf(10000), List.of(
                            new OrderItemSnapshot(1L, 1, "상품",
                                    BigDecimal.valueOf(10000), "brand-id", "브랜드", null,
                                    BigDecimal.valueOf(10000), BigDecimal.ZERO, BigDecimal.valueOf(10000))));

            ArgumentCaptor<OrderModel> captor = ArgumentCaptor.forClass(OrderModel.class);
            verify(orderRepository).save(captor.capture());
            assertThat(captor.getValue().getOrderType()).isEqualTo(OrderType.CART);
        }

        @Test
        @DisplayName("totalAmount가 전달한 값으로 설정된다")
        void createOrder_ShouldSetTotalAmount() {
            when(orderRepository.save(any(OrderModel.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));
            when(orderItemRepository.saveAll(anyList()))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            orderService.createOrder(1L, OrderType.DIRECT,
                    BigDecimal.valueOf(30000), List.of(
                            new OrderItemSnapshot(1L, 3, "상품",
                                    BigDecimal.valueOf(10000), "brand-id", "브랜드", null,
                                    BigDecimal.valueOf(30000), BigDecimal.ZERO, BigDecimal.valueOf(30000))));

            ArgumentCaptor<OrderModel> captor = ArgumentCaptor.forClass(OrderModel.class);
            verify(orderRepository).save(captor.capture());
            assertThat(captor.getValue().getTotalAmount())
                    .isEqualByComparingTo(BigDecimal.valueOf(30000));
        }
    }

    // === 주문 취소 ===

    @Nested
    @DisplayName("주문 취소 (cancelOrder)")
    class CancelOrderTests {

        @Test
        @DisplayName("CAS 상태 전이 성공 시 주문 엔티티를 반환한다")
        void cancelOrder_ShouldReturnOrder_WhenCASSucceeds() {
            OrderModel order = OrderModel.create(1L, OrderType.DIRECT, BigDecimal.valueOf(10000));
            when(orderRepository.findByIdAndUserId(1L, 1L))
                    .thenReturn(Optional.of(order));
            when(orderRepository.casUpdateStatus(1L, OrderStatus.PENDING_PAYMENT, OrderStatus.CANCELLED))
                    .thenReturn(1);

            Optional<OrderModel> result = orderService.cancelOrder(1L, 1L);

            assertThat(result).isPresent();
            verify(orderRepository).casUpdateStatus(1L, OrderStatus.PENDING_PAYMENT, OrderStatus.CANCELLED);
        }

        @Test
        @DisplayName("다른 사용자의 주문 취소 시 예외가 발생한다")
        void cancelOrder_WhenNotOwner_ShouldThrow() {
            when(orderRepository.findByIdAndUserId(1L, 2L))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> orderService.cancelOrder(2L, 1L))
                    .isInstanceOf(CoreException.class)
                    .satisfies(ex -> assertThat(((CoreException) ex).getErrorType())
                            .isEqualTo(ErrorType.ORDER_NOT_FOUND));
        }

        @Test
        @DisplayName("이미 CANCELLED인 주문 취소 시 빈 Optional을 반환한다 (멱등)")
        void cancelOrder_WhenAlreadyCancelled_ShouldReturnEmpty() {
            OrderModel order = OrderModel.create(1L, OrderType.DIRECT, BigDecimal.valueOf(10000));
            when(orderRepository.findByIdAndUserId(1L, 1L))
                    .thenReturn(Optional.of(order));
            when(orderRepository.casUpdateStatus(1L, OrderStatus.PENDING_PAYMENT, OrderStatus.CANCELLED))
                    .thenReturn(0);
            OrderModel cancelledOrder = OrderModel.create(1L, OrderType.DIRECT, BigDecimal.valueOf(10000));
            cancelledOrder.cancel();
            when(orderRepository.findById(1L)).thenReturn(Optional.of(cancelledOrder));

            Optional<OrderModel> result = orderService.cancelOrder(1L, 1L);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("EXPIRED 상태인 주문 취소 시 ORDER_NOT_CANCELLABLE 예외가 발생한다")
        void cancelOrder_WhenExpired_ShouldThrow_ORDER_NOT_CANCELLABLE() {
            OrderModel order = OrderModel.create(1L, OrderType.DIRECT, BigDecimal.valueOf(10000));
            when(orderRepository.findByIdAndUserId(1L, 1L))
                    .thenReturn(Optional.of(order));
            when(orderRepository.casUpdateStatus(1L, OrderStatus.PENDING_PAYMENT, OrderStatus.CANCELLED))
                    .thenReturn(0);
            OrderModel expiredOrder = OrderModel.create(1L, OrderType.DIRECT, BigDecimal.valueOf(10000));
            expiredOrder.expire();
            when(orderRepository.findById(1L)).thenReturn(Optional.of(expiredOrder));

            assertThatThrownBy(() -> orderService.cancelOrder(1L, 1L))
                    .isInstanceOf(CoreException.class)
                    .satisfies(ex -> assertThat(((CoreException) ex).getErrorType())
                            .isEqualTo(ErrorType.ORDER_NOT_CANCELLABLE));
        }
    }

    // === 주문 만료 ===

    @Nested
    @DisplayName("주문 만료 (expireOrder)")
    class ExpireOrderTests {

        @Test
        @DisplayName("CAS 상태 전이 성공 시 주문 엔티티를 반환한다")
        void expireOrder_ShouldReturnOrder_WhenCASSucceeds() {
            when(orderRepository.casUpdateStatus(1L, OrderStatus.PENDING_PAYMENT, OrderStatus.EXPIRED))
                    .thenReturn(1);
            OrderModel order = OrderModel.create(1L, OrderType.CART, BigDecimal.valueOf(10000));
            when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

            Optional<OrderModel> result = orderService.expireOrder(1L);

            assertThat(result).isPresent();
            verify(orderRepository).casUpdateStatus(1L, OrderStatus.PENDING_PAYMENT, OrderStatus.EXPIRED);
        }

        @Test
        @DisplayName("CAS 실패 시 빈 Optional을 반환한다 (멱등)")
        void expireOrder_AlreadyExpiredOrCancelled_ShouldReturnEmpty() {
            when(orderRepository.casUpdateStatus(1L, OrderStatus.PENDING_PAYMENT, OrderStatus.EXPIRED))
                    .thenReturn(0);

            Optional<OrderModel> result = orderService.expireOrder(1L);

            assertThat(result).isEmpty();
        }
    }

    // === 조회 ===

    @Nested
    @DisplayName("주문 조회")
    class QueryTests {

        @Test
        @DisplayName("본인 주문 조회 성공")
        void findByIdAndUserId_Existing_ShouldReturn() {
            OrderModel order = OrderModel.create(1L, OrderType.DIRECT, BigDecimal.valueOf(10000));
            when(orderRepository.findByIdAndUserId(1L, 1L))
                    .thenReturn(Optional.of(order));

            OrderModel result = orderService.findByIdAndUserId(1L, 1L);

            assertThat(result).isNotNull();
            assertThat(result.getUserId()).isEqualTo(1L);
        }

        @Test
        @DisplayName("내 주문 목록 조회 성공")
        void findAllByUserId_ShouldReturnOrders() {
            OrderModel order = OrderModel.create(1L, OrderType.DIRECT, BigDecimal.valueOf(10000));
            when(orderRepository.findAllByUserIdAndPeriod(eq(1L), any(), any()))
                    .thenReturn(List.of(order));

            List<OrderModel> result = orderService.findAllByUserId(1L,
                    LocalDateTime.now().minusDays(30), LocalDateTime.now());

            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("주문 항목 조회 성공")
        void findOrderItems_ShouldReturnItems() {
            OrderItemModel item = OrderItemModel.create(1L, 1, 1L, 1L, 2,
                    "상품명", BigDecimal.valueOf(10000), "brand-id", "브랜드", null);
            when(orderItemRepository.findAllByOrderId(1L)).thenReturn(List.of(item));

            List<OrderItemModel> result = orderService.findOrderItems(1L);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getProductId()).isEqualTo(1L);
        }
    }
}
