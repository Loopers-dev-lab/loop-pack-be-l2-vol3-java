package com.loopers.application.order;

import com.loopers.application.order.command.CreateOrderCommand;
import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.brand.vo.BrandName;
import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderRepository;
import com.loopers.domain.order.OrderStatus;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderApplicationServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private BrandRepository brandRepository;

    @InjectMocks
    private OrderApplicationService orderApplicationService;

    private static final Brand SAMPLE_BRAND = new Brand(10L, new BrandName("퍼피박스"), "설명", "http://img.com");
    private static final Product SAMPLE_PRODUCT = new Product(1L, "강아지 사료", 10000, 20, "desc", 1L, 10L, 0, null);

    @Nested
    @DisplayName("주문 생성")
    class Create {

        @Test
        @DisplayName("유효한 상품과 재고로 주문 생성 성공")
        void createOrderSuccess() {
            CreateOrderCommand command = new CreateOrderCommand(
                    1L,
                    List.of(new CreateOrderCommand.OrderItemCommand(1L, 2))
            );

            when(productRepository.findAllByIdInWithLock(List.of(1L)))
                    .thenReturn(List.of(SAMPLE_PRODUCT));
            when(brandRepository.findById(10L))
                    .thenReturn(Optional.of(SAMPLE_BRAND));
            when(productRepository.save(any(Product.class)))
                    .thenReturn(SAMPLE_PRODUCT.decreaseStock(2));
            when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

            Order result = orderApplicationService.create(command);

            assertThat(result.status()).isEqualTo(OrderStatus.ORDERED);
            assertThat(result.userId()).isEqualTo(1L);
            assertThat(result.items()).hasSize(1);
        }

        @Test
        @DisplayName("주문 항목이 비어있으면 400 예외가 발생한다")
        void emptyItemsFails() {
            CreateOrderCommand command = new CreateOrderCommand(1L, List.of());

            assertThatThrownBy(() -> orderApplicationService.create(command))
                    .isInstanceOf(CoreException.class)
                    .satisfies(ex -> assertThat(((CoreException) ex).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));
        }

        @Test
        @DisplayName("존재하지 않는 상품을 주문하면 404 예외가 발생한다")
        void nonExistentProductFails() {
            CreateOrderCommand command = new CreateOrderCommand(
                    1L,
                    List.of(new CreateOrderCommand.OrderItemCommand(999L, 1))
            );

            when(productRepository.findAllByIdInWithLock(anyList())).thenReturn(List.of());

            assertThatThrownBy(() -> orderApplicationService.create(command))
                    .isInstanceOf(CoreException.class)
                    .satisfies(ex -> assertThat(((CoreException) ex).getErrorType()).isEqualTo(ErrorType.NOT_FOUND));
        }
    }

    @Nested
    @DisplayName("주문 취소")
    class Cancel {

        @Test
        @DisplayName("이미 취소된 주문 재취소는 409 예외가 발생한다")
        void cancelAlreadyCancelledOrderFails() {
            Order cancelledOrder = new Order(
                    1L, 1L, "ORDER-001",
                    java.time.ZonedDateTime.now(),
                    OrderStatus.CANCELLED,
                    10000,
                    List.of(new com.loopers.domain.order.OrderItem(1L, 1L, 1L, 1, "사료", 10000, "퍼피박스")),
                    null
            );

            when(orderRepository.findById(1L)).thenReturn(Optional.of(cancelledOrder));

            assertThatThrownBy(() -> orderApplicationService.cancel(1L, 1L, false))
                    .isInstanceOf(CoreException.class)
                    .satisfies(ex -> assertThat(((CoreException) ex).getErrorType()).isEqualTo(ErrorType.CONFLICT));
        }

        @Test
        @DisplayName("타인의 주문 취소 시 403 예외가 발생한다")
        void cancelOthersOrderFails() {
            Order order = new Order(
                    1L, 2L, "ORDER-001",
                    java.time.ZonedDateTime.now(),
                    OrderStatus.ORDERED,
                    10000,
                    List.of(new com.loopers.domain.order.OrderItem(1L, 1L, 1L, 1, "사료", 10000, "퍼피박스")),
                    null
            );

            when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

            assertThatThrownBy(() -> orderApplicationService.cancel(1L, 1L, false))
                    .isInstanceOf(CoreException.class)
                    .satisfies(ex -> assertThat(((CoreException) ex).getErrorType()).isEqualTo(ErrorType.FORBIDDEN));
        }

        @Test
        @DisplayName("존재하지 않는 주문 취소 시 404 예외가 발생한다")
        void cancelNonExistentOrderFails() {
            when(orderRepository.findById(anyLong())).thenReturn(Optional.empty());

            assertThatThrownBy(() -> orderApplicationService.cancel(99L, 1L, false))
                    .isInstanceOf(CoreException.class)
                    .satisfies(ex -> assertThat(((CoreException) ex).getErrorType()).isEqualTo(ErrorType.NOT_FOUND));
        }
    }

    @Nested
    @DisplayName("주문 상세 조회")
    class GetById {

        @Test
        @DisplayName("타인의 주문 조회 시 403 예외가 발생한다")
        void getOthersOrderFails() {
            Order order = new Order(
                    1L, 2L, "ORDER-001",
                    java.time.ZonedDateTime.now(),
                    OrderStatus.ORDERED,
                    10000,
                    List.of(new com.loopers.domain.order.OrderItem(1L, 1L, 1L, 1, "사료", 10000, "퍼피박스")),
                    null
            );

            when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

            assertThatThrownBy(() -> orderApplicationService.getById(1L, 1L, false))
                    .isInstanceOf(CoreException.class)
                    .satisfies(ex -> assertThat(((CoreException) ex).getErrorType()).isEqualTo(ErrorType.FORBIDDEN));
        }

        @Test
        @DisplayName("존재하지 않는 주문 조회 시 404 예외가 발생한다")
        void getNonExistentOrderFails() {
            when(orderRepository.findById(anyLong())).thenReturn(Optional.empty());

            assertThatThrownBy(() -> orderApplicationService.getById(99L, 1L, false))
                    .isInstanceOf(CoreException.class)
                    .satisfies(ex -> assertThat(((CoreException) ex).getErrorType()).isEqualTo(ErrorType.NOT_FOUND));
        }
    }
}
