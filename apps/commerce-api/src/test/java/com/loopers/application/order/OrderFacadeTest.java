package com.loopers.application.order;

import com.loopers.application.order.dto.CreateOrderReqDto;
import com.loopers.application.order.dto.FindOrderResDto;
import com.loopers.domain.member.model.Member;
import com.loopers.domain.member.service.MemberService;
import com.loopers.domain.order.model.OrderCommand;
import com.loopers.domain.order.model.OrderProduct;
import com.loopers.domain.order.model.Orders;
import com.loopers.domain.order.service.OrderProductService;
import com.loopers.domain.order.service.OrderService;
import com.loopers.domain.product.model.Product;
import com.loopers.domain.product.service.ProductService;
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

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderFacadeTest {

    @InjectMocks
    private OrderFacade orderFacade;

    @Mock
    private OrderService orderService;

    @Mock
    private OrderProductService orderProductService;

    @Mock
    private MemberService memberService;

    @Mock
    private ProductService productService;

    private static Member createTestMember() {
        return Member.reconstruct(1L, "testuser", "encodedPw", "홍길동", LocalDate.of(1990, 1, 1), "test@test.com");
    }

    private static Product createTestProduct(Long id, Long brandId, String name, int price, int stock) {
        return Product.reconstruct(id, brandId, name, price, stock, "DISPLAYING");
    }

    @DisplayName("주문 생성")
    @Nested
    class CreateOrder {

        @DisplayName("여러 상품을 정상적으로 주문하고 재고가 차감된다")
        @Test
        void createsOrder_withMultipleProducts() {
            // arrange
            Member member = createTestMember();
            Product product1 = createTestProduct(1L, 1L, "상품A", 10000, 100);
            Product product2 = createTestProduct(2L, 1L, "상품B", 5000, 50);

            CreateOrderReqDto dto = new CreateOrderReqDto(List.of(
                    new CreateOrderReqDto.OrderItemReqDto(1L, 2),
                    new CreateOrderReqDto.OrderItemReqDto(2L, 3)
            ));

            when(memberService.findMember("testuser", "password")).thenReturn(member);
            when(productService.getProductsByIds(List.of(1L, 2L))).thenReturn(List.of(product1, product2));
            when(orderService.createOrder(any(OrderCommand.Create.class))).thenAnswer(invocation -> {
                OrderCommand.Create command = invocation.getArgument(0);
                return Orders.reconstruct(1L, command.memberId(), 35000, command.orderProducts());
            });
            when(orderProductService.saveAll(eq(1L), any())).thenAnswer(invocation -> {
                List<OrderProduct> products = invocation.getArgument(1);
                return products;
            });

            // act
            FindOrderResDto result = orderFacade.createOrder("testuser", "password", dto);

            // assert
            assertAll(
                () -> assertThat(result.totalPrice()).isEqualTo(35000),
                () -> assertThat(result.orderProducts()).hasSize(2)
            );
            verify(productService).decreaseStock(product1, 2);
            verify(productService).decreaseStock(product2, 3);

            // verify the command passed to orderService
            ArgumentCaptor<OrderCommand.Create> captor = ArgumentCaptor.forClass(OrderCommand.Create.class);
            verify(orderService).createOrder(captor.capture());
            OrderCommand.Create captured = captor.getValue();
            assertThat(captured.memberId()).isEqualTo(1L);
            assertThat(captured.orderProducts()).hasSize(2);
        }

        @DisplayName("존재하지 않는 상품이 포함되면 예외가 발생한다")
        @Test
        void throwsException_whenProductNotFound() {
            // arrange
            Member member = createTestMember();

            CreateOrderReqDto dto = new CreateOrderReqDto(List.of(
                    new CreateOrderReqDto.OrderItemReqDto(999L, 1)
            ));

            when(memberService.findMember("testuser", "password")).thenReturn(member);
            when(productService.getProductsByIds(List.of(999L)))
                    .thenThrow(new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 상품입니다."));

            // act & assert
            assertThatThrownBy(() -> orderFacade.createOrder("testuser", "password", dto))
                .isInstanceOf(CoreException.class)
                .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.NOT_FOUND));
        }

        @DisplayName("재고가 부족하면 예외가 발생한다")
        @Test
        void throwsException_whenInsufficientStock() {
            // arrange
            Member member = createTestMember();
            Product product = createTestProduct(1L, 1L, "상품A", 10000, 5);

            CreateOrderReqDto dto = new CreateOrderReqDto(List.of(
                    new CreateOrderReqDto.OrderItemReqDto(1L, 10)
            ));

            when(memberService.findMember("testuser", "password")).thenReturn(member);
            when(productService.getProductsByIds(List.of(1L))).thenReturn(List.of(product));
            doThrow(new CoreException(ErrorType.BAD_REQUEST, "재고가 부족합니다."))
                    .when(productService).decreaseStock(product, 10);

            // act & assert
            assertThatThrownBy(() -> orderFacade.createOrder("testuser", "password", dto))
                .isInstanceOf(CoreException.class)
                .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));
        }
    }

    @DisplayName("기간별 주문 조회")
    @Nested
    class GetOrders {

        @DisplayName("인증에 실패하면 CoreException(UNAUTHORIZED)이 발생한다")
        @Test
        void throwsException_whenAuthFails() {
            // arrange
            when(memberService.findMember("testuser", "wrongpw"))
                    .thenThrow(new CoreException(ErrorType.UNAUTHORIZED, "인증에 실패했습니다."));

            // act & assert
            assertThatThrownBy(() -> orderFacade.getOrders("testuser", "wrongpw", null, null))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.UNAUTHORIZED));
        }

        @DisplayName("정상 조회 시 populateAndConvert로 orderProducts가 포함된 결과를 반환한다")
        @Test
        void returnsOrdersWithProducts() {
            // arrange
            Member member = createTestMember();
            Orders orders = Orders.reconstruct(1L, member.getId(), 10000, List.of());
            OrderProduct orderProduct = OrderProduct.reconstruct(1L, 10L, "상품A", 10000, 1);

            when(memberService.findMember("testuser", "password")).thenReturn(member);
            when(orderService.getOrders(any(OrderCommand.GetByPeriod.class))).thenReturn(List.of(orders));
            when(orderProductService.findByOrderId(1L)).thenReturn(List.of(orderProduct));

            // act
            List<FindOrderResDto> result = orderFacade.getOrders("testuser", "password", null, null);

            // assert
            assertAll(
                () -> assertThat(result).hasSize(1),
                () -> assertThat(result.get(0).orderProducts()).hasSize(1)
            );
        }
    }

    @DisplayName("주문 단건 조회")
    @Nested
    class GetOrder {

        @DisplayName("인증에 실패하면 CoreException(UNAUTHORIZED)이 발생한다")
        @Test
        void throwsException_whenAuthFails() {
            // arrange
            when(memberService.findMember("testuser", "wrongpw"))
                    .thenThrow(new CoreException(ErrorType.UNAUTHORIZED, "인증에 실패했습니다."));

            // act & assert
            assertThatThrownBy(() -> orderFacade.getOrder("testuser", "wrongpw", 1L))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.UNAUTHORIZED));
        }

        @DisplayName("정상 조회 시 populateAndConvert로 orderProducts가 포함된 결과를 반환한다")
        @Test
        void returnsOrderWithProducts() {
            // arrange
            Member member = createTestMember();
            Orders orders = Orders.reconstruct(1L, member.getId(), 20000, List.of());
            OrderProduct orderProduct1 = OrderProduct.reconstruct(1L, 10L, "상품A", 10000, 1);
            OrderProduct orderProduct2 = OrderProduct.reconstruct(2L, 11L, "상품B", 10000, 1);

            when(memberService.findMember("testuser", "password")).thenReturn(member);
            when(orderService.getOrder(any(OrderCommand.GetByMember.class))).thenReturn(orders);
            when(orderProductService.findByOrderId(1L)).thenReturn(List.of(orderProduct1, orderProduct2));

            // act
            FindOrderResDto result = orderFacade.getOrder("testuser", "password", 1L);

            // assert
            assertAll(
                () -> assertThat(result.id()).isEqualTo(1L),
                () -> assertThat(result.orderProducts()).hasSize(2)
            );
        }
    }
}
