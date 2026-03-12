package com.loopers.application.order;

import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderService;
import com.loopers.domain.product.Money;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.stock.StockModel;
import com.loopers.domain.stock.StockService;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class OrderFacadeTest {

    @InjectMocks
    private OrderFacade orderFacade;

    @Mock
    private OrderService orderService;

    @Mock
    private ProductService productService;

    @Mock
    private StockService stockService;

    @DisplayName("주문 생성")
    @Nested
    class CreateOrder {

        @DisplayName("정상적으로 주문을 생성한다")
        @Test
        void createsOrderSuccessfully() {
            // arrange
            Long memberId = 1L;
            ProductModel product1 = new ProductModel("에어맥스", "러닝화", new Money(129000), 1L);
            ReflectionTestUtils.setField(product1, "id", 1L);
            ProductModel product2 = new ProductModel("에어포스", "캐주얼", new Money(109000), 1L);
            ReflectionTestUtils.setField(product2, "id", 2L);
            StockModel stock1 = new StockModel(1L, 100);
            StockModel stock2 = new StockModel(2L, 50);

            given(productService.getById(1L)).willReturn(product1);
            given(productService.getById(2L)).willReturn(product2);
            given(stockService.getByProductId(1L)).willReturn(stock1);
            given(stockService.getByProductId(2L)).willReturn(stock2);
            given(orderService.save(any(OrderModel.class))).willAnswer(invocation -> invocation.getArgument(0));

            List<OrderItemCommand> commands = List.of(
                new OrderItemCommand(1L, 2),
                new OrderItemCommand(2L, 1)
            );
            // act
            OrderInfo.Detail result = orderFacade.createOrder(memberId, commands);
            // assert
            assertAll(
                () -> assertThat(result.totalAmount()).isEqualTo(129000 * 2 + 109000),
                () -> assertThat(result.orderItems()).hasSize(2)
            );
        }

        @DisplayName("삭제된 상품이 포함되면 NOT_FOUND 예외가 발생한다")
        @Test
        void throwsOnDeletedProduct() {
            // arrange
            Long memberId = 1L;
            ProductModel product = new ProductModel("에어맥스", "러닝화", new Money(129000), 1L);
            ReflectionTestUtils.setField(product, "id", 1L);
            product.delete();
            given(productService.getById(1L)).willReturn(product);

            List<OrderItemCommand> commands = List.of(new OrderItemCommand(1L, 1));
            // act
            CoreException exception = assertThrows(CoreException.class, () -> {
                orderFacade.createOrder(memberId, commands);
            });
            // assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
            then(orderService).should(never()).save(any());
        }

        @DisplayName("재고가 부족하면 BAD_REQUEST 예외가 발생한다")
        @Test
        void throwsOnInsufficientStock() {
            // arrange
            Long memberId = 1L;
            ProductModel product = new ProductModel("에어맥스", "러닝화", new Money(129000), 1L);
            ReflectionTestUtils.setField(product, "id", 1L);
            StockModel stock = new StockModel(1L, 5);

            given(productService.getById(1L)).willReturn(product);
            given(stockService.getByProductId(1L)).willReturn(stock);

            List<OrderItemCommand> commands = List.of(new OrderItemCommand(1L, 10));
            // act
            CoreException exception = assertThrows(CoreException.class, () -> {
                orderFacade.createOrder(memberId, commands);
            });
            // assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
            then(orderService).should(never()).save(any());
        }
    }
}
