package com.loopers.interfaces.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.loopers.application.order.OrderFacade;
import com.loopers.application.order.dto.OrderCriteria;
import com.loopers.application.order.dto.OrderResult;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.auth.LoginUser;
import com.loopers.interfaces.order.dto.OrderRequest;
import com.loopers.interfaces.order.dto.OrderResponse;
import java.time.ZonedDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@DisplayName("OrderV1Controller 단위 테스트")
@ExtendWith(MockitoExtension.class)
class OrderV1ControllerTest {

    @Mock
    private OrderFacade orderFacade;

    @InjectMocks
    private OrderV1Controller orderV1Controller;

    private final LoginUser loginUser = new LoginUser(1L, "testuser", "테스터");

    @DisplayName("POST /api/v1/orders")
    @Nested
    class CreateOrder {

        @DisplayName("주문 생성 요청이면, SUCCESS 응답을 반환한다")
        @Test
        void create_returnsCreatedResponse() {
            // arrange
            OrderRequest.Create request = new OrderRequest.Create(List.of(
                new OrderRequest.OrderItemRequest(10L, 2, 25000)),
                null);

            OrderResult.OrderSummary result = new OrderResult.OrderSummary(
                1L, 50000, 50000, 0, "ORDERED", ZonedDateTime.now()
            );
            when(orderFacade.createOrder(eq(1L), any(OrderCriteria.Create.class))).thenReturn(result);

            // act
            ApiResponse<OrderResponse.OrderSummary> response = orderV1Controller.create(loginUser, request);

            // assert
            assertAll(
                () -> assertThat(response.meta().result()).isEqualTo(ApiResponse.Metadata.Result.SUCCESS),
                () -> assertThat(response.data().orderId()).isEqualTo(1L),
                () -> assertThat(response.data().totalPrice()).isEqualTo(50000),
                () -> assertThat(response.data().discountAmount()).isEqualTo(0)
            );
        }

        @DisplayName("쿠폰 적용 주문 생성 요청이면, 할인 정보가 포함된 응답을 반환한다")
        @Test
        void create_withCoupon_returnsDiscountInfo() {
            // arrange
            OrderRequest.Create request = new OrderRequest.Create(List.of(
                new OrderRequest.OrderItemRequest(10L, 2, 25000)),
                42L);

            OrderResult.OrderSummary result = new OrderResult.OrderSummary(
                1L, 45000, 50000, 5000, "ORDERED", ZonedDateTime.now()
            );
            when(orderFacade.createOrder(eq(1L), any(OrderCriteria.Create.class))).thenReturn(result);

            // act
            ApiResponse<OrderResponse.OrderSummary> response = orderV1Controller.create(loginUser, request);

            // assert
            assertAll(
                () -> assertThat(response.meta().result()).isEqualTo(ApiResponse.Metadata.Result.SUCCESS),
                () -> assertThat(response.data().totalPrice()).isEqualTo(45000),
                () -> assertThat(response.data().originalTotalPrice()).isEqualTo(50000),
                () -> assertThat(response.data().discountAmount()).isEqualTo(5000)
            );
        }
    }

    @DisplayName("GET /api/v1/orders")
    @Nested
    class ListOrders {

        @DisplayName("기간 내 주문 목록을 200 응답으로 반환한다")
        @Test
        void list_returnsOrderList() {
            // arrange
            ZonedDateTime startAt = ZonedDateTime.now().minusDays(7);
            ZonedDateTime endAt = ZonedDateTime.now();

            List<OrderResult.OrderSummary> results = List.of(
                new OrderResult.OrderSummary(1L, 50000, 50000, 0, "ORDERED", ZonedDateTime.now())
            );
            when(orderFacade.getMyOrders(eq(1L), any(OrderCriteria.ListByDate.class))).thenReturn(results);

            // act
            ApiResponse<OrderResponse.ListResponse> response = orderV1Controller.list(loginUser, startAt, endAt);

            // assert
            assertAll(
                () -> assertThat(response.meta().result()).isEqualTo(ApiResponse.Metadata.Result.SUCCESS),
                () -> assertThat(response.data().items()).hasSize(1)
            );
        }
    }

    @DisplayName("GET /api/v1/orders/{orderId}")
    @Nested
    class GetOrderDetail {

        @DisplayName("주문 상세를 200 응답으로 반환한다")
        @Test
        void getById_returnsOrderDetail() {
            // arrange
            OrderResult.OrderDetail result = new OrderResult.OrderDetail(
                1L, 1L, 50000, "ORDERED", ZonedDateTime.now(),
                List.of(new OrderResult.OrderItemDetail(1L, 10L, "상품A", "브랜드A", 25000, 2))
            );
            when(orderFacade.getMyOrderDetail(1L, 1L)).thenReturn(result);

            // act
            ApiResponse<OrderResponse.OrderDetail> response = orderV1Controller.getById(loginUser, 1L);

            // assert
            assertAll(
                () -> assertThat(response.meta().result()).isEqualTo(ApiResponse.Metadata.Result.SUCCESS),
                () -> assertThat(response.data().orderId()).isEqualTo(1L),
                () -> assertThat(response.data().items()).hasSize(1)
            );
        }
    }
}
