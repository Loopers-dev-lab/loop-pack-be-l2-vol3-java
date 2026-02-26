package com.loopers.interfaces.api.order;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.interfaces.api.brand.dto.BrandCreateRequest;
import com.loopers.interfaces.api.order.dto.DeliveryAddressUpdateRequest;
import com.loopers.interfaces.api.order.dto.OrderCreateRequest;
import com.loopers.interfaces.api.product.dto.ProductCreateRequest;
import com.loopers.interfaces.api.user.dto.UserRegisterRequest;
import com.loopers.testcontainers.MySqlTestContainersConfig;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Import(MySqlTestContainersConfig.class)
class OrderApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    private static final String ORDER_URL = "/api/v1/orders";
    private static final String ADMIN_ORDER_URL = "/api-admin/v1/orders";
    private static final String ADMIN_HEADER = "X-Loopers-Ldap";
    private static final String ADMIN_VALUE = "loopers.admin";
    private static final String LOGIN_ID = "testuser1";
    private static final String PASSWORD = "Password1!";

    @BeforeEach
    void setUp() throws Exception {
        databaseCleanUp.truncateAllTables();
        registerUser(LOGIN_ID, PASSWORD, "홍길동");
        createBrand("나이키", "스포츠");
        createProduct(1L, "운동화", 50000, 100);
    }

    @Nested
    @DisplayName("주문 생성 API")
    class CreateOrderApi {

        @Test
        @DisplayName("주문 생성 성공")
        void createOrder_success() throws Exception {
            var request = new OrderCreateRequest(
                    List.of(new OrderCreateRequest.OrderItemRequest(1L, 2)),
                    "홍길동", "서울시 강남구", "문 앞에 놓아주세요",
                    "CARD", LocalDate.now().plusDays(3)
            );

            mockMvc.perform(post(ORDER_URL)
                            .header("X-Loopers-LoginId", LOGIN_ID)
                            .header("X-Loopers-LoginPw", PASSWORD)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("인증 없이 주문 생성 시 실패")
        void createOrder_fail_unauthorized() throws Exception {
            var request = new OrderCreateRequest(
                    List.of(new OrderCreateRequest.OrderItemRequest(1L, 2)),
                    "홍길동", "서울시", "요청", "CARD", LocalDate.now()
            );

            mockMvc.perform(post(ORDER_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("주문 조회 API")
    class QueryOrderApi {

        @Test
        @DisplayName("내 주문 목록 조회 성공")
        void getMyOrders_success() throws Exception {
            createOrder();

            mockMvc.perform(get(ORDER_URL)
                            .header("X-Loopers-LoginId", LOGIN_ID)
                            .header("X-Loopers-LoginPw", PASSWORD))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].status").value("PAYMENT_COMPLETED"));
        }

        @Test
        @DisplayName("주문 상세 조회 성공")
        void getOrderDetail_success() throws Exception {
            createOrder();

            mockMvc.perform(get(ORDER_URL + "/1")
                            .header("X-Loopers-LoginId", LOGIN_ID)
                            .header("X-Loopers-LoginPw", PASSWORD))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.receiverName").value("홍길동"))
                    .andExpect(jsonPath("$.status").value("PAYMENT_COMPLETED"))
                    .andExpect(jsonPath("$.items.length()").value(1));
        }

        @Test
        @DisplayName("기간 필터 조회")
        void getMyOrders_withDateRange() throws Exception {
            createOrder();

            String startAt = LocalDate.now().minusDays(1).toString();
            String endAt = LocalDate.now().plusDays(1).toString();

            mockMvc.perform(get(ORDER_URL)
                            .header("X-Loopers-LoginId", LOGIN_ID)
                            .header("X-Loopers-LoginPw", PASSWORD)
                            .param("startAt", startAt)
                            .param("endAt", endAt))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1));
        }
    }

    @Nested
    @DisplayName("주문 취소 API")
    class CancelOrderApi {

        @Test
        @DisplayName("주문 취소 성공")
        void cancelOrder_success() throws Exception {
            createOrder();

            mockMvc.perform(post(ORDER_URL + "/1/cancel")
                            .header("X-Loopers-LoginId", LOGIN_ID)
                            .header("X-Loopers-LoginPw", PASSWORD))
                    .andExpect(status().isOk());

            // 취소 상태 확인
            mockMvc.perform(get(ORDER_URL + "/1")
                            .header("X-Loopers-LoginId", LOGIN_ID)
                            .header("X-Loopers-LoginPw", PASSWORD))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("CANCELLED"));
        }
    }

    @Nested
    @DisplayName("배송지 변경 API")
    class UpdateDeliveryAddressApi {

        @Test
        @DisplayName("배송지 변경 성공")
        void updateDeliveryAddress_success() throws Exception {
            createOrder();

            var request = new DeliveryAddressUpdateRequest("부산시 해운대구");

            mockMvc.perform(put(ORDER_URL + "/1/delivery-address")
                            .header("X-Loopers-LoginId", LOGIN_ID)
                            .header("X-Loopers-LoginPw", PASSWORD)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk());

            // 변경 확인
            mockMvc.perform(get(ORDER_URL + "/1")
                            .header("X-Loopers-LoginId", LOGIN_ID)
                            .header("X-Loopers-LoginPw", PASSWORD))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.address").value("부산시 해운대구"));
        }
    }

    @Nested
    @DisplayName("관리자 주문 조회 API")
    class AdminOrderApi {

        @Test
        @DisplayName("관리자 전체 주문 목록 조회")
        void getAllOrders_success() throws Exception {
            createOrder();

            mockMvc.perform(get(ADMIN_ORDER_URL)
                            .header(ADMIN_HEADER, ADMIN_VALUE))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1));
        }

        @Test
        @DisplayName("관리자 주문 상세 조회")
        void getOrderDetail_admin() throws Exception {
            createOrder();

            mockMvc.perform(get(ADMIN_ORDER_URL + "/1")
                            .header(ADMIN_HEADER, ADMIN_VALUE))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.receiverName").value("홍길동"));
        }

        @Test
        @DisplayName("관리자 인증 없이 조회 시 실패")
        void getAllOrders_fail_unauthorized() throws Exception {
            mockMvc.perform(get(ADMIN_ORDER_URL))
                    .andExpect(status().isUnauthorized());
        }
    }

    private void registerUser(String loginId, String password, String name) throws Exception {
        var request = new UserRegisterRequest(loginId, password, name,
                LocalDate.of(1990, 5, 15), "test@example.com");
        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    private void createBrand(String name, String description) throws Exception {
        var request = new BrandCreateRequest(name, description);
        mockMvc.perform(post("/api-admin/v1/brands")
                        .header(ADMIN_HEADER, ADMIN_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    private void createProduct(Long brandId, String name, int price, int stock) throws Exception {
        var request = new ProductCreateRequest(brandId, name, price, null, stock, "설명");
        mockMvc.perform(post("/api-admin/v1/products")
                        .header(ADMIN_HEADER, ADMIN_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    private void createOrder() throws Exception {
        var request = new OrderCreateRequest(
                List.of(new OrderCreateRequest.OrderItemRequest(1L, 2)),
                "홍길동", "서울시 강남구", "문 앞에 놓아주세요",
                "CARD", LocalDate.now().plusDays(3)
        );
        mockMvc.perform(post(ORDER_URL)
                        .header("X-Loopers-LoginId", LOGIN_ID)
                        .header("X-Loopers-LoginPw", PASSWORD)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }
}
