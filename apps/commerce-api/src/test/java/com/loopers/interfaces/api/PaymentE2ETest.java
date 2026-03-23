package com.loopers.interfaces.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.application.order.OrderDto;
import com.loopers.application.order.OrderFacade;
import com.loopers.application.user.UserDto;
import com.loopers.application.user.UserFacade;
import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandDescription;
import com.loopers.domain.brand.BrandName;
import com.loopers.domain.user.Gender;
import com.loopers.infrastructure.brand.BrandJpaRepository;
import com.loopers.infrastructure.pg.PgClient;
import com.loopers.infrastructure.product.ProductJpaRepository;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PaymentE2ETest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserFacade userFacade;

    @Autowired
    private OrderFacade orderFacade;

    @Autowired
    private BrandJpaRepository brandJpaRepository;

    @Autowired
    private ProductJpaRepository productJpaRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @MockitoBean
    private PgClient pgClient;

    private String loginId = "payapi1";
    private String password = "TestPass1!";
    private Long orderId;

    @BeforeEach
    void setUp() {
        UserDto.UserInfo user = userFacade.register(
            loginId, password, "결제API테스터",
            LocalDate.of(2000, 1, 1), "payapi1@loopers.com", Gender.MALE
        );

        Brand brand = brandJpaRepository.save(
            Brand.create(new BrandName("PAY_API_BRAND"), new BrandDescription("결제 API 브랜드"))
        );
        Long productId = productJpaRepository.save(
            com.loopers.domain.product.Product.create(
                brand.getId(), "결제 API 상품", "설명", BigDecimal.valueOf(15000), 10
            )
        ).getId();

        OrderDto.OrderInfo order = orderFacade.placeOrder(
            user.id(),
            List.of(new OrderDto.OrderLineCommand(productId, 1)),
            null
        );
        orderId = order.id();
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("POST /api/v1/payments: 결제 요청에 성공하면 PENDING 상태의 결제가 반환된다")
    @Test
    void pay_success() throws Exception {
        when(pgClient.requestPayment(anyLong(), anyLong(), anyString(), anyString(), any()))
            .thenReturn(Optional.of("20250816:TR:e2e001"));

        Map<String, Object> body = new HashMap<>();
        body.put("orderId", orderId);
        body.put("cardType", "SAMSUNG");
        body.put("cardNo", "1234-5678-9814-1451");

        mockMvc.perform(post("/api/v1/payments")
                .header("X-Loopers-LoginId", loginId)
                .header("X-Loopers-LoginPw", password)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.meta.result").value("SUCCESS"))
            .andExpect(jsonPath("$.data.orderId").value(orderId))
            .andExpect(jsonPath("$.data.status").value("PENDING"))
            .andExpect(jsonPath("$.data.pgTransactionId").value("20250816:TR:e2e001"));
    }

    @DisplayName("POST /api/v1/payments: 잘못된 비밀번호면 인증 실패 응답이 반환된다")
    @Test
    void pay_fail_invalidPassword() throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("orderId", orderId);
        body.put("cardType", "SAMSUNG");
        body.put("cardNo", "1234-5678-9814-1451");

        mockMvc.perform(post("/api/v1/payments")
                .header("X-Loopers-LoginId", loginId)
                .header("X-Loopers-LoginPw", "wrongpassword")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
            .andDo(print())
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.meta.result").value("FAIL"));
    }

    @DisplayName("POST /api/v1/payments: 존재하지 않는 주문이면 NOT FOUND 응답이 반환된다")
    @Test
    void pay_fail_orderNotFound() throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("orderId", 99999L);
        body.put("cardType", "SAMSUNG");
        body.put("cardNo", "1234-5678-9814-1451");

        mockMvc.perform(post("/api/v1/payments")
                .header("X-Loopers-LoginId", loginId)
                .header("X-Loopers-LoginPw", password)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
            .andDo(print())
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.meta.result").value("FAIL"));
    }

    @DisplayName("POST /api/v1/payments/callback: PG 콜백 수신 시 결제 상태가 반영된다")
    @Test
    void callback_success() throws Exception {
        when(pgClient.requestPayment(anyLong(), anyLong(), anyString(), anyString(), any()))
            .thenReturn(Optional.of("20250816:TR:e2e002"));

        // 결제 요청
        Map<String, Object> payBody = Map.of(
            "orderId", orderId,
            "cardType", "SAMSUNG",
            "cardNo", "1234-5678-9814-1451"
        );
        mockMvc.perform(post("/api/v1/payments")
                .header("X-Loopers-LoginId", loginId)
                .header("X-Loopers-LoginPw", password)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payBody)))
            .andExpect(status().isOk());

        // 콜백 수신
        Map<String, Object> callbackBody = Map.of(
            "transactionId", "20250816:TR:e2e002",
            "orderId", String.valueOf(orderId),
            "status", "SUCCESS"
        );
        mockMvc.perform(post("/api/v1/payments/callback")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(callbackBody)))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.meta.result").value("SUCCESS"))
            .andExpect(jsonPath("$.data.status").value("SUCCESS"));
    }

    @DisplayName("POST /api-admin/v1/payments/{orderId}/sync: PENDING 결제를 PG 조회 후 수동 동기화한다")
    @Test
    void syncPayment_success() throws Exception {
        when(pgClient.requestPayment(anyLong(), anyLong(), anyString(), anyString(), any()))
            .thenReturn(Optional.empty()); // PG 요청 실패
        when(pgClient.getPaymentByOrderId(anyLong(), anyLong()))
            .thenReturn(Optional.empty()); // 초기 복구도 실패

        // 결제 요청 (PENDING으로 저장됨)
        Map<String, Object> payBody = Map.of(
            "orderId", orderId,
            "cardType", "SAMSUNG",
            "cardNo", "1234-5678-9814-1451"
        );
        mockMvc.perform(post("/api/v1/payments")
                .header("X-Loopers-LoginId", loginId)
                .header("X-Loopers-LoginPw", password)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payBody)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("PENDING"));

        // 관리자 동기화
        when(pgClient.getPaymentByOrderId(anyLong(), eq(orderId)))
            .thenReturn(Optional.of(new com.loopers.infrastructure.pg.PgPaymentInfo(
                "txId-sync", String.valueOf(orderId), "SUCCESS", 15000L
            )));

        mockMvc.perform(post("/api-admin/v1/payments/" + orderId + "/sync"))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.meta.result").value("SUCCESS"))
            .andExpect(jsonPath("$.data.status").value("SUCCESS"));
    }
}
