package com.loopers.interfaces.api.order;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.application.order.queue.OrderAdmissionApplicationService;
import com.loopers.testcontainers.MySqlTestContainersConfig;
import com.loopers.testcontainers.RedisTestContainersConfig;
import com.loopers.utils.DatabaseCleanUp;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
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

@SpringBootTest(properties = {
        "loopers.queue.order.enabled=true",
        "loopers.queue.order.throughput-per-second=2"
})
@AutoConfigureMockMvc
@ImportTestcontainers({MySqlTestContainersConfig.class, RedisTestContainersConfig.class})
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

    @Autowired
    private RedisCleanUp redisCleanUp;

    @Autowired
    private OrderAdmissionApplicationService orderAdmissionApplicationService;

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
        redisCleanUp.truncateAll();
    }

    @Nested
    @DisplayName("POST /api/v1/order-queue")
    class EnterQueue {

        @Test
        @DisplayName("대기열 진입 시 순번과 예상 대기 시간을 반환한다")
        void enterQueueSuccess() throws Exception {
            mockMvc.perform(post("/api/v1/order-queue")
                            .header(HEADER_LOGIN_ID, TEST_LOGIN_ID)
                            .header(HEADER_LOGIN_PW, TEST_PASSWORD))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.meta.result").value("SUCCESS"))
                    .andExpect(jsonPath("$.data.enabled").value(true))
                    .andExpect(jsonPath("$.data.waitingOrder").value(1))
                    .andExpect(jsonPath("$.data.estimatedWaitSeconds").value(0));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/order-queue/me")
    class GetQueueStatus {

        @Test
        @DisplayName("대기열 재진입 시 기존 엔트리를 갱신해 뒤로 밀린 순번을 반환한다")
        void reEnterQueueMovesUserBack() throws Exception {
            String secondLoginId = "testuser2";
            var secondRegisterRequest = new com.loopers.interfaces.api.member.MemberDto.RegisterRequest(
                    secondLoginId, TEST_PASSWORD, "테스터", "19900101", "test2@example.com", "010-9999-5678"
            );
            mockMvc.perform(post("/api/v1/members")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(secondRegisterRequest)));

            mockMvc.perform(post("/api/v1/order-queue")
                    .header(HEADER_LOGIN_ID, TEST_LOGIN_ID)
                    .header(HEADER_LOGIN_PW, TEST_PASSWORD));

            mockMvc.perform(post("/api/v1/order-queue")
                    .header(HEADER_LOGIN_ID, secondLoginId)
                    .header(HEADER_LOGIN_PW, TEST_PASSWORD));

            mockMvc.perform(post("/api/v1/order-queue")
                            .header(HEADER_LOGIN_ID, TEST_LOGIN_ID)
                            .header(HEADER_LOGIN_PW, TEST_PASSWORD))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.waitingOrder").value(2));

            mockMvc.perform(get("/api/v1/order-queue/me")
                            .header(HEADER_LOGIN_ID, TEST_LOGIN_ID)
                            .header(HEADER_LOGIN_PW, TEST_PASSWORD))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.waitingOrder").value(2))
                    .andExpect(jsonPath("$.data.estimatedWaitSeconds").value(0));
        }

        @Test
        @DisplayName("대기열에 진입하지 않은 사용자가 조회하면 404를 반환한다")
        void getStatusWithoutEnteringFails() throws Exception {
            mockMvc.perform(get("/api/v1/order-queue/me")
                            .header(HEADER_LOGIN_ID, TEST_LOGIN_ID)
                            .header(HEADER_LOGIN_PW, TEST_PASSWORD))
                    .andExpect(status().isNotFound());
        }
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
        @DisplayName("입장 토큰이 없으면 주문 생성이 403을 반환한다")
        void createOrderWithoutTokenFails() throws Exception {
            OrderDto.CreateOrderRequest request = new OrderDto.CreateOrderRequest(
                    List.of(new OrderDto.OrderItemRequest(UUID.randomUUID(), 1))
            );

            mockMvc.perform(post("/api/v1/orders")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request))
                            .header(HEADER_LOGIN_ID, TEST_LOGIN_ID)
                            .header(HEADER_LOGIN_PW, TEST_PASSWORD))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("토큰 발급 후 존재하지 않는 상품 주문 시 404를 반환한다")
        void createOrderWithTokenAndUnknownProductFailsWithNotFound() throws Exception {
            mockMvc.perform(post("/api/v1/order-queue")
                    .header(HEADER_LOGIN_ID, TEST_LOGIN_ID)
                    .header(HEADER_LOGIN_PW, TEST_PASSWORD));
            orderAdmissionApplicationService.issueAdmissions();

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
