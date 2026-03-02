package com.loopers.domain.order;

import com.loopers.domain.product.ProductService;
import com.loopers.domain.product.ProductSnapshot;
import com.loopers.domain.product.ProductValidationRequest;
import com.loopers.domain.product.Quantity;
import com.loopers.domain.product.RestoreStockItem;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import com.loopers.domain.product.Money;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long ORDER_ID = 100L;
    private static final Long OTHER_USER_ID = 999L;
    private static final ProductSnapshot SNAPSHOT = new ProductSnapshot(10L, "상품", Money.of(new BigDecimal("5000")));
    private static final List<ProductValidationRequest> REQUESTS = List.of(
            new ProductValidationRequest(10L, Quantity.of(2), null));

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private ProductService productService;

    @InjectMocks
    private OrderService orderService;

    @DisplayName("create 시")
    @Nested
    class Create {

        @Test
        void create_withValidInputs_shouldValidateSaveAndReturn() {
            // given
            when(productService.validateAndGetSnapshots(REQUESTS)).thenReturn(List.of(SNAPSHOT));
            OrderModel order = OrderModel.create(USER_ID);
            order.addItem(OrderItemModel.of(SNAPSHOT, Quantity.of(2), null));
            when(orderRepository.save(any(OrderModel.class))).thenReturn(order);

            // when
            OrderModel result = orderService.create(USER_ID, REQUESTS);

            // then
            assertThat(result).isNotNull();
            verify(productService).validateAndGetSnapshots(REQUESTS);
            verify(orderRepository).save(any(OrderModel.class));
        }

        @Test
        void create_withNullUserId_shouldThrowBadRequest() {
            CoreException ex = assertThrows(CoreException.class, () -> orderService.create(null, REQUESTS));
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
            verify(productService, never()).validateAndGetSnapshots(any());
            verify(orderRepository, never()).save(any());
        }

        @Test
        void create_withNullRequests_shouldThrowBadRequest() {
            CoreException ex = assertThrows(CoreException.class, () -> orderService.create(USER_ID, null));
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
            verify(productService, never()).validateAndGetSnapshots(any());
        }

        @Test
        void create_withEmptyRequests_shouldThrowBadRequest() {
            CoreException ex = assertThrows(CoreException.class, () -> orderService.create(USER_ID, List.of()));
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
            verify(productService, never()).validateAndGetSnapshots(any());
        }

        @Test
        void create_whenProductServiceThrowsNotFound_shouldPropagate() {
            when(productService.validateAndGetSnapshots(REQUESTS))
                    .thenThrow(new CoreException(ErrorType.NOT_FOUND, "상품을 찾을 수 없습니다."));
            CoreException ex = assertThrows(CoreException.class, () -> orderService.create(USER_ID, REQUESTS));
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
            verify(orderRepository, never()).save(any());
        }
    }

    @DisplayName("findById 시")
    @Nested
    class FindById {

        @Test
        void findById_whenFoundAndOwner_shouldReturnPresent() {
            // given
            OrderModel order = OrderModel.create(USER_ID);
            when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

            // when
            Optional<OrderModel> result = orderService.findById(USER_ID, ORDER_ID);

            // then
            assertThat(result).isPresent();
            assertThat(result.get().getUserId()).isEqualTo(USER_ID);
        }

        @Test
        void findById_whenNotFound_shouldReturnEmpty() {
            when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.empty());
            assertThat(orderService.findById(USER_ID, ORDER_ID)).isEmpty();
        }

        @Test
        void findById_whenWrongUser_shouldReturnEmpty() {
            OrderModel order = OrderModel.create(OTHER_USER_ID);
            when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
            assertThat(orderService.findById(USER_ID, ORDER_ID)).isEmpty();
        }
    }

    @DisplayName("findOrders 시")
    @Nested
    class FindOrders {

        @Test
        void findOrders_shouldDelegateToRepository() {
            ZonedDateTime start = ZonedDateTime.now().minusDays(1);
            ZonedDateTime end = ZonedDateTime.now();
            List<OrderModel> orders = List.of(OrderModel.create(USER_ID));
            when(orderRepository.findByUserIdAndOrderedAtBetween(eq(USER_ID), eq(start), eq(end), eq(0), eq(10)))
                    .thenReturn(orders);

            List<OrderModel> result = orderService.findOrders(USER_ID, start, end, 0, 10);

            assertThat(result).hasSize(1);
            verify(orderRepository).findByUserIdAndOrderedAtBetween(USER_ID, start, end, 0, 10);
        }
    }

    @DisplayName("cancel 시")
    @Nested
    class Cancel {

        @Test
        void cancel_whenOrdered_shouldCancelWithoutRestoreStock() {
            // given
            OrderModel order = OrderModel.create(USER_ID);
            order.addItem(OrderItemModel.of(SNAPSHOT, Quantity.of(1), null));
            when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
            when(orderRepository.save(any(OrderModel.class))).thenAnswer(inv -> inv.getArgument(0));

            // when
            OrderModel result = orderService.cancel(USER_ID, ORDER_ID);

            // then
            assertThat(result.getStatus()).isEqualTo(OrderStatus.CANCELLED);
            verify(productService, never()).restoreStock(any());
            verify(orderRepository).save(order);
        }

        @Test
        void cancel_whenPaid_shouldRestoreStockThenCancel() {
            // given: PAID 상태 주문
            OrderModel order = OrderModel.withStatus(USER_ID, OrderStatus.PAID, java.time.ZonedDateTime.now());
            order.addItem(OrderItemModel.of(SNAPSHOT, Quantity.of(2), null));
            when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
            when(orderRepository.save(any(OrderModel.class))).thenAnswer(inv -> inv.getArgument(0));

            // when
            OrderModel result = orderService.cancel(USER_ID, ORDER_ID);

            // then: 재고 복구 호출 후 취소
            assertThat(result.getStatus()).isEqualTo(OrderStatus.CANCELLED);
            verify(productService).restoreStock(argThat((List<RestoreStockItem> items) -> items.size() == 1
                    && items.get(0).productId().equals(10L) && items.get(0).quantity().value() == 2));
            verify(orderRepository).save(order);
        }

        @Test
        void cancel_whenPaidWithMultipleItems_shouldRestoreStockForAllItemsThenCancel() {
            // given: PAID 주문에 항목 2개
            ProductSnapshot snap1 = new ProductSnapshot(10L, "상품1", Money.of(new BigDecimal("5000")));
            ProductSnapshot snap2 = new ProductSnapshot(20L, "상품2", Money.of(new BigDecimal("3000")));
            OrderModel order = OrderModel.withStatus(USER_ID, OrderStatus.PAID, java.time.ZonedDateTime.now());
            order.addItem(OrderItemModel.of(snap1, Quantity.of(1), null));
            order.addItem(OrderItemModel.of(snap2, Quantity.of(3), null));
            when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
            when(orderRepository.save(any(OrderModel.class))).thenAnswer(inv -> inv.getArgument(0));

            // when
            OrderModel result = orderService.cancel(USER_ID, ORDER_ID);

            // then
            assertThat(result.getStatus()).isEqualTo(OrderStatus.CANCELLED);
            verify(productService).restoreStock(argThat((List<RestoreStockItem> items) -> items.size() == 2
                    && items.stream().anyMatch(i -> i.productId().equals(10L) && i.quantity().value() == 1)
                    && items.stream().anyMatch(i -> i.productId().equals(20L) && i.quantity().value() == 3)));
            verify(orderRepository).save(order);
        }

        @Test
        void cancel_whenOrderNotFound_shouldThrowNotFound() {
            when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.empty());
            CoreException ex = assertThrows(CoreException.class, () -> orderService.cancel(USER_ID, ORDER_ID));
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
            verify(orderRepository, never()).save(any());
        }

        @Test
        void cancel_whenWrongUser_shouldThrowNotFound() {
            OrderModel order = OrderModel.create(OTHER_USER_ID);
            when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
            CoreException ex = assertThrows(CoreException.class, () -> orderService.cancel(USER_ID, ORDER_ID));
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
            verify(orderRepository, never()).save(any());
        }

        @Test
        void cancel_whenAlreadyCancelled_shouldThrowBadRequest() {
            OrderModel order = OrderModel.create(USER_ID);
            order.addItem(OrderItemModel.of(SNAPSHOT, Quantity.of(1), null));
            order.cancel();
            when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
            CoreException ex = assertThrows(CoreException.class, () -> orderService.cancel(USER_ID, ORDER_ID));
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
            verify(orderRepository, never()).save(any());
        }
    }
}
