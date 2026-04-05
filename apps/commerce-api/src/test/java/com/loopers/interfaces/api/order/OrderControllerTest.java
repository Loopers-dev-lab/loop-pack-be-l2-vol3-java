package com.loopers.interfaces.api.order;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.application.order.OrderDetail;
import com.loopers.application.order.OrderFacade;
import com.loopers.application.order.OrderSummary;
import com.loopers.application.queue.QueueFacade;
import com.loopers.application.user.UserFacade;
import com.loopers.config.WebMvcConfig;
import com.loopers.interfaces.auth.AuthArgumentResolver;
import com.loopers.interfaces.auth.AuthInterceptor;
import com.loopers.interfaces.auth.EntryTokenInterceptor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.ZonedDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import({AuthInterceptor.class, EntryTokenInterceptor.class, AuthArgumentResolver.class, WebMvcConfig.class})
@WebMvcTest(OrderController.class)
class OrderControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @MockitoBean
    OrderFacade orderFacade;

    @MockitoBean
    UserFacade userFacade;

    @MockitoBean
    QueueFacade queueFacade;

    @Nested
    @DisplayName("POST /api/v1/orders 요청 시, ")
    class CreateOrder {

        @DisplayName("입장 토큰이 없으면 403을 반환한다.")
        @Test
        void returnsForbidden_whenEntryTokenIsMissing() throws Exception {
            // arrange
            when(userFacade.authenticate("testUser1", "test1234!")).thenReturn(1L);

            // act & assert
            mockMvc.perform(post("/api/v1/orders")
                            .contentType(APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(createOrderRequest()))
                            .header("X-Loopers-LoginId", "testUser1")
                            .header("X-Loopers-LoginPw", "test1234!"))
                    .andExpect(status().isForbidden());
        }

        @DisplayName("유효한 입장 토큰이 있으면 주문을 생성한다.")
        @Test
        void createsOrder_whenEntryTokenIsValid() throws Exception {
            // arrange
            when(userFacade.authenticate("testUser1", "test1234!")).thenReturn(1L);
            when(queueFacade.validateToken(1L, "valid-token")).thenReturn(true);
            when(orderFacade.createOrder(anyLong(), any())).thenReturn("20260403-ABCDEF");

            // act & assert
            mockMvc.perform(post("/api/v1/orders")
                            .contentType(APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(createOrderRequest()))
                            .header("X-Loopers-LoginId", "testUser1")
                            .header("X-Loopers-LoginPw", "test1234!")
                            .header("X-Loopers-EntryToken", "valid-token"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.orderId").value("20260403-ABCDEF"));

            verify(queueFacade).validateToken(1L, "valid-token");
            verify(orderFacade).createOrder(anyLong(), any());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/orders 요청 시, ")
    class GetList {

        @DisplayName("입장 토큰 없이도 로그인만으로 조회할 수 있다.")
        @Test
        void returnsOk_withoutEntryToken() throws Exception {
            // arrange
            when(userFacade.authenticate("testUser1", "test1234!")).thenReturn(1L);
            when(orderFacade.getList(anyLong(), any(), any()))
                    .thenReturn(List.of(new OrderSummary("20260403-ABCDEF", ZonedDateTime.now(), 10000L, 1)));

            // act & assert
            mockMvc.perform(get("/api/v1/orders")
                            .param("startAt", "2026-04-01")
                            .param("endAt", "2026-04-03")
                            .header("X-Loopers-LoginId", "testUser1")
                            .header("X-Loopers-LoginPw", "test1234!"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[0].orderId").value("20260403-ABCDEF"));

            verify(queueFacade, never()).validateToken(anyLong(), anyString());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/orders/{orderId} 요청 시, ")
    class GetDetail {

        @DisplayName("입장 토큰 없이도 로그인만으로 조회할 수 있다.")
        @Test
        void returnsOk_withoutEntryToken() throws Exception {
            // arrange
            when(userFacade.authenticate("testUser1", "test1234!")).thenReturn(1L);
            when(orderFacade.getDetail(1L, "20260403-ABCDEF"))
                    .thenReturn(new OrderDetail(1L, 10000L, List.of(
                            new OrderDetail.OrderItemDetail("product", "brand", 10000, 1)
                    )));

            // act & assert
            mockMvc.perform(get("/api/v1/orders/20260403-ABCDEF")
                            .header("X-Loopers-LoginId", "testUser1")
                            .header("X-Loopers-LoginPw", "test1234!"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.orderId").value(1L));

            verify(queueFacade, never()).validateToken(anyLong(), anyString());
        }
    }

    private OrderDto.CreateOrderRequest createOrderRequest() {
        return new OrderDto.CreateOrderRequest(
                List.of(new OrderDto.CreateOrderRequest.OrderItem(1L, 2)),
                null
        );
    }
}
