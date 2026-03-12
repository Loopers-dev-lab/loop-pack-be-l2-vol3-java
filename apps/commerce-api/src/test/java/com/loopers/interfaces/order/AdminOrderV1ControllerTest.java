package com.loopers.interfaces.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.loopers.application.order.OrderFacade;
import com.loopers.application.order.dto.OrderResult;
import com.loopers.interfaces.api.ApiResponse;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

@DisplayName("AdminOrderV1Controller 단위 테스트")
@ExtendWith(MockitoExtension.class)
class AdminOrderV1ControllerTest {

    @Mock
    private OrderFacade orderFacade;

    @InjectMocks
    private AdminOrderV1Controller adminOrderV1Controller;

    @DisplayName("GET /api-admin/v1/orders")
    @Nested
    class ListOrders {

        @DisplayName("전체 주문 목록을 200 응답으로 반환한다")
        @Test
        void list_returnsPageResponse() {
            // arrange
            OrderResult.OrderSummary summary = new OrderResult.OrderSummary(
                1L, 50000, 50000, 0, "ORDERED", ZonedDateTime.now()
            );
            Page<OrderResult.OrderSummary> page = new PageImpl<>(
                List.of(summary), PageRequest.of(0, 20), 1
            );
            when(orderFacade.getAllOrders(any())).thenReturn(page);

            // act
            ApiResponse<OrderResponse.PageResponse> response = adminOrderV1Controller.list(0, 20);

            // assert
            assertAll(
                () -> assertThat(response.meta().result()).isEqualTo(ApiResponse.Metadata.Result.SUCCESS),
                () -> assertThat(response.data().totalElements()).isEqualTo(1),
                () -> assertThat(response.data().items()).hasSize(1)
            );
        }
    }

    @DisplayName("GET /api-admin/v1/orders/{orderId}")
    @Nested
    class GetOrderDetail {

        @DisplayName("주문 상세를 200 응답으로 반환한다")
        @Test
        void getById_returnsOrderDetail() {
            // arrange
            OrderResult.OrderDetail result = new OrderResult.OrderDetail(
                1L, 2L, 50000, "ORDERED", ZonedDateTime.now(),
                List.of(new OrderResult.OrderItemDetail(1L, 10L, "상품A", "브랜드A", 25000, 2))
            );
            when(orderFacade.getOrderDetail(1L)).thenReturn(result);

            // act
            ApiResponse<OrderResponse.OrderDetail> response = adminOrderV1Controller.getById(1L);

            // assert
            assertAll(
                () -> assertThat(response.meta().result()).isEqualTo(ApiResponse.Metadata.Result.SUCCESS),
                () -> assertThat(response.data().orderId()).isEqualTo(1L),
                () -> assertThat(response.data().userId()).isEqualTo(2L),
                () -> assertThat(response.data().items()).hasSize(1)
            );
        }
    }
}
