package com.loopers.interfaces.api.order;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.testcontainers.MySqlTestContainersConfig;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import java.util.UUID;

@SpringBootTest
@AutoConfigureMockMvc
@Import(MySqlTestContainersConfig.class)
@ActiveProfiles("test")
class OrderControllerTest {

    private static final String HEADER_LOGIN_ID = "X-Loopers-LoginId";
    private static final String HEADER_LOGIN_PW = "X-Loopers-LoginPw";
    private static final String TEST_LOGIN_ID = "testuser1";
    private static final String TEST_PASSWORD = "Test1234!@";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @BeforeEach
    void setUp() throws Exception {
        // 테스트 유저 등록
        var registerRequest = new com.loopers.interfaces.api.member.MemberDto.RegisterRequest(
                TEST_LOGIN_ID, TEST_PASSWORD, "테스터", "19900101", "test@example.com", "010-1234-5678"
        );
        mockMvc.perform(post("/api/v1/members")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registerRequest)));
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @Nested
    @DisplayName("POST /api/v1/orders")
    class CreateOrder {

        @Test
        @DisplayName("인증 없이 주문하면 401을 반환한다")
        void createOrderWithoutAuthFails() throws Exception {
            OrderDto.CreateOrderRequest request = new OrderDto.CreateOrderRequest(
                    List.of(new OrderDto.OrderItemRequest(UUID.randomUUID(), 1))
            );

            mockMvc.perform(post("/api/v1/orders")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("빈 items로 주문하면 400을 반환한다")
        void createOrderWithEmptyItemsFails() throws Exception {
            OrderDto.CreateOrderRequest request = new OrderDto.CreateOrderRequest(List.of());

            mockMvc.perform(post("/api/v1/orders")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request))
                            .header(HEADER_LOGIN_ID, TEST_LOGIN_ID)
                            .header(HEADER_LOGIN_PW, TEST_PASSWORD))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("존재하지 않는 상품 주문 시 404를 반환한다")
        void createOrderWithNonExistentProductFails() throws Exception {
            OrderDto.CreateOrderRequest request = new OrderDto.CreateOrderRequest(
                    List.of(new OrderDto.OrderItemRequest(UUID.randomUUID(), 1))
            );

            mockMvc.perform(post("/api/v1/orders")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request))
                            .header(HEADER_LOGIN_ID, TEST_LOGIN_ID)
                            .header(HEADER_LOGIN_PW, TEST_PASSWORD))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("PATCH /api/v1/orders/{orderId}/cancel")
    class CancelOrder {

        @Test
        @DisplayName("존재하지 않는 주문 취소 시 404를 반환한다")
        void cancelNonExistentOrderFails() throws Exception {
            mockMvc.perform(patch("/api/v1/orders/{orderId}/cancel", UUID.randomUUID())
                            .header(HEADER_LOGIN_ID, TEST_LOGIN_ID)
                            .header(HEADER_LOGIN_PW, TEST_PASSWORD))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("인증 없이 취소하면 401을 반환한다")
        void cancelWithoutAuthFails() throws Exception {
            mockMvc.perform(patch("/api/v1/orders/{orderId}/cancel", UUID.randomUUID()))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/orders")
    class ListOrders {

        @Test
        @DisplayName("startAt/endAt 누락 시 400을 반환한다")
        void listOrdersWithoutDateParamsFails() throws Exception {
            mockMvc.perform(get("/api/v1/orders")
                            .header(HEADER_LOGIN_ID, TEST_LOGIN_ID)
                            .header(HEADER_LOGIN_PW, TEST_PASSWORD))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("유효한 날짜로 주문 목록 조회 성공")
        void listOrdersSuccess() throws Exception {
            mockMvc.perform(get("/api/v1/orders")
                            .param("startAt", "20260101")
                            .param("endAt", "20261231")
                            .param("page", "0")
                            .param("size", "20")
                            .header(HEADER_LOGIN_ID, TEST_LOGIN_ID)
                            .header(HEADER_LOGIN_PW, TEST_PASSWORD))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.meta.result").value("SUCCESS"))
                    .andExpect(jsonPath("$.data.items").isArray());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/orders/{orderId}")
    class GetOrderDetail {

        @Test
        @DisplayName("존재하지 않는 주문 조회 시 404를 반환한다")
        void getNonExistentOrderFails() throws Exception {
            mockMvc.perform(get("/api/v1/orders/{orderId}", UUID.randomUUID())
                            .header(HEADER_LOGIN_ID, TEST_LOGIN_ID)
                            .header(HEADER_LOGIN_PW, TEST_PASSWORD))
                    .andExpect(status().isNotFound());
        }
    }
}
