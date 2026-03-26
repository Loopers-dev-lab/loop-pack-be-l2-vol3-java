package com.loopers.interfaces.api.payment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.order.OrderModel;
import com.loopers.domain.payment.GatewayPaymentResult;
import com.loopers.domain.payment.PaymentGateway;
import com.loopers.domain.payment.PaymentModel;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductStockModel;
import com.loopers.domain.user.UserModel;
import com.loopers.infrastructure.brand.BrandJpaRepository;
import com.loopers.infrastructure.order.OrderJpaRepository;
import com.loopers.infrastructure.payment.PaymentJpaRepository;
import com.loopers.infrastructure.product.ProductJpaRepository;
import com.loopers.infrastructure.product.ProductStockJpaRepository;
import com.loopers.infrastructure.user.UserJpaRepository;
import com.loopers.interfaces.api.user.UserV1Dto;
import com.loopers.support.enums.CardType;
import com.loopers.support.enums.OrderType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("Payment API V1 E2E 테스트")
class PaymentV1ApiE2ETest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired BrandJpaRepository brandJpaRepository;
    @Autowired ProductJpaRepository productJpaRepository;
    @Autowired ProductStockJpaRepository productStockJpaRepository;
    @Autowired OrderJpaRepository orderJpaRepository;
    @Autowired PaymentJpaRepository paymentJpaRepository;
    @Autowired UserJpaRepository userJpaRepository;

    @MockitoBean PaymentGateway paymentGateway;

    private static final String LOGIN_ID = "paymentuser01";
    private static final String LOGIN_PW = "Test1234!@#";
    private static final String TRANSACTION_KEY = "txn-e2e-test-123";

    private Long orderId;
    private Long userId;

    @BeforeEach
    void setUp() throws Exception {
        registerUser(LOGIN_ID, LOGIN_PW, "결제테스트유저");

        UserModel user = userJpaRepository.findByLoginId(LOGIN_ID).orElseThrow();
        userId = user.getUserId();

        BrandModel brand = brandJpaRepository.save(
                BrandModel.create("테스트브랜드", "설명", "서울"));

        ProductModel product = productJpaRepository.save(
                ProductModel.create("테스트상품", brand.getBrandId(),
                        BigDecimal.valueOf(10000), "상품설명", null, null, null, null, null, null));

        productStockJpaRepository.save(ProductStockModel.create(product.getProductId(), 100));

        OrderModel order = orderJpaRepository.save(
                OrderModel.create(userId, OrderType.DIRECT, BigDecimal.valueOf(30000)));
        orderId = order.getOrderId();
    }

    @Nested
    @DisplayName("POST /api/v1/payments - 결제 요청")
    class RequestPaymentTests {

        @Test
        @DisplayName("정상 결제 요청 시 200 반환")
        void POST_requestPayment_ShouldReturn200() throws Exception {
            when(paymentGateway.requestPayment(any(), any(), any(), any(), any(), any()))
                    .thenReturn(new GatewayPaymentResult(TRANSACTION_KEY, true, "PENDING", null));

            var request = new PaymentV1Dto.PaymentRequest(orderId, CardType.SAMSUNG, "1234-5678-9012-3456");

            mockMvc.perform(post("/api/v1/payments")
                            .header("X-Loopers-LoginId", LOGIN_ID)
                            .header("X-Loopers-LoginPw", LOGIN_PW)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.meta.result").value("SUCCESS"))
                    .andExpect(jsonPath("$.data.transactionKey").value(TRANSACTION_KEY))
                    .andExpect(jsonPath("$.data.status").value("PENDING"))
                    .andExpect(jsonPath("$.data.orderId").value(orderId));
        }

        @Test
        @DisplayName("인증 없이 결제 요청 시 401 반환")
        void POST_requestPayment_WithoutAuth_ShouldReturn401() throws Exception {
            var request = new PaymentV1Dto.PaymentRequest(orderId, CardType.SAMSUNG, "1234-5678-9012-3456");

            mockMvc.perform(post("/api/v1/payments")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("잘못된 인증 정보로 결제 요청 시 401 반환")
        void POST_requestPayment_WrongAuth_ShouldReturn401() throws Exception {
            var request = new PaymentV1Dto.PaymentRequest(orderId, CardType.SAMSUNG, "1234-5678-9012-3456");

            mockMvc.perform(post("/api/v1/payments")
                            .header("X-Loopers-LoginId", LOGIN_ID)
                            .header("X-Loopers-LoginPw", "WrongPass123!")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("POST /api/v1/payments/callback - PG 콜백 수신")
    class HandleCallbackTests {

        @Test
        @DisplayName("SUCCESS 콜백 수신 시 200 반환")
        void POST_callback_Success_ShouldReturn200() throws Exception {
            // 결제 레코드 직접 생성 (PG 호출 없이)
            PaymentModel payment = paymentJpaRepository.save(
                    PaymentModel.create(orderId, userId, CardType.SAMSUNG, "1234-5678-9012-3456",
                            BigDecimal.valueOf(30000)));
            payment.assignTransactionKey(TRANSACTION_KEY);
            paymentJpaRepository.saveAndFlush(payment);

            var callback = new PaymentV1Dto.CallbackRequest(TRANSACTION_KEY, "SUCCESS", null);

            mockMvc.perform(post("/api/v1/payments/callback")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(callback)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.meta.result").value("SUCCESS"));
        }

        @Test
        @DisplayName("FAILED 콜백 수신 시 200 반환")
        void POST_callback_Failed_ShouldReturn200() throws Exception {
            PaymentModel payment = paymentJpaRepository.save(
                    PaymentModel.create(orderId, userId, CardType.SAMSUNG, "1234-5678-9012-3456",
                            BigDecimal.valueOf(30000)));
            payment.assignTransactionKey(TRANSACTION_KEY);
            paymentJpaRepository.saveAndFlush(payment);

            var callback = new PaymentV1Dto.CallbackRequest(TRANSACTION_KEY, "FAILED", "잔액 부족");

            mockMvc.perform(post("/api/v1/payments/callback")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(callback)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.meta.result").value("SUCCESS"));
        }

        @Test
        @DisplayName("잘못된 transactionKey 콜백 시 404 반환")
        void POST_callback_InvalidKey_ShouldReturn404() throws Exception {
            var callback = new PaymentV1Dto.CallbackRequest("invalid-key", "SUCCESS", null);

            mockMvc.perform(post("/api/v1/payments/callback")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(callback)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.meta.errorCode").value("PAYMENT_NOT_FOUND"));
        }

        @Test
        @DisplayName("콜백은 인증 없이 수신 가능")
        void POST_callback_WithoutAuth_ShouldReturn200OrError() throws Exception {
            var callback = new PaymentV1Dto.CallbackRequest("any-key", "SUCCESS", null);

            // 인증 없이도 요청 자체는 처리됨 (404는 정상 — 인증 문제가 아님)
            mockMvc.perform(post("/api/v1/payments/callback")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(callback)))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/payments/{paymentId} - 결제 조회")
    class GetPaymentTests {

        @Test
        @DisplayName("정상 결제 조회 시 200 반환")
        void GET_payment_ShouldReturn200() throws Exception {
            PaymentModel payment = paymentJpaRepository.save(
                    PaymentModel.create(orderId, userId, CardType.SAMSUNG, "1234-5678-9012-3456",
                            BigDecimal.valueOf(30000)));

            mockMvc.perform(get("/api/v1/payments/{paymentId}", payment.getPaymentId())
                            .header("X-Loopers-LoginId", LOGIN_ID)
                            .header("X-Loopers-LoginPw", LOGIN_PW))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.meta.result").value("SUCCESS"))
                    .andExpect(jsonPath("$.data.paymentId").value(payment.getPaymentId()))
                    .andExpect(jsonPath("$.data.orderId").value(orderId))
                    .andExpect(jsonPath("$.data.status").value("REQUESTED"));
        }

        @Test
        @DisplayName("존재하지 않는 결제 조회 시 404 반환")
        void GET_payment_NotFound_ShouldReturn404() throws Exception {
            mockMvc.perform(get("/api/v1/payments/{paymentId}", 999L)
                            .header("X-Loopers-LoginId", LOGIN_ID)
                            .header("X-Loopers-LoginPw", LOGIN_PW))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.meta.errorCode").value("PAYMENT_NOT_FOUND"));
        }
    }

    private void registerUser(String loginId, String password, String userName) throws Exception {
        var request = UserV1Dto.RegisterRequest.builder()
                .loginId(loginId)
                .password(password)
                .userName(userName)
                .birthday("19900101")
                .email("test@example.com")
                .address("서울")
                .build();

        mockMvc.perform(post("/api/v1/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)));
    }
}
