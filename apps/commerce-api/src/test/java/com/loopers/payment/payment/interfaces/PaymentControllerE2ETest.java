package com.loopers.payment.payment.interfaces;


import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.catalog.brand.interfaces.web.request.AdminBrandCreateRequest;
import com.loopers.catalog.product.interfaces.web.request.AdminProductCreateRequest;
import com.loopers.ordering.order.interfaces.web.request.OrderCreateRequest;
import com.loopers.payment.payment.application.service.PaymentQueryService;
import com.loopers.payment.payment.domain.model.Payment;
import com.loopers.payment.payment.domain.model.enums.PaymentStatus;
import com.loopers.payment.payment.interfaces.web.request.PaymentCreateRequest;
import com.loopers.support.common.error.ErrorType;
import com.loopers.testcontainers.MySqlTestContainersConfig;
import com.loopers.testcontainers.RedisTestContainersConfig;
import com.loopers.user.user.interfaces.web.request.UserSignUpRequest;
import com.loopers.utils.DatabaseCleanUp;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import com.loopers.user.user.infrastructure.jpa.UserJpaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


@SpringBootTest(
	webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT,
	properties = {
		"server.port=18082",
		"payment.pg.base-url=http://localhost:18082/test-pg"
	}
)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({MySqlTestContainersConfig.class, RedisTestContainersConfig.class})
@DisplayName("Payment E2E 테스트 (TestPgSimulator)")
class PaymentControllerE2ETest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private DatabaseCleanUp databaseCleanUp;

	@Autowired
	private PaymentQueryService paymentQueryService;

	@Autowired
	private CircuitBreakerRegistry circuitBreakerRegistry;

	@Qualifier("redisTemplateMaster")
	@Autowired
	private RedisTemplate<String, String> redisTemplate;

	@Autowired
	private UserJpaRepository userJpaRepository;

	private static final String USER_LOGIN_ID_HEADER = "X-Loopers-LoginId";
	private static final String USER_LOGIN_PW_HEADER = "X-Loopers-LoginPw";
	private static final String ADMIN_LDAP_HEADER = "X-Loopers-Ldap";
	private static final String ADMIN_LDAP_VALUE = "loopers.admin";
	private static final String TEST_PASSWORD = "Test1234!";

	private Long orderId;

	// payment.pg.base-url=http://localhost:18082/test-pg (properties에서 설정)
	// TestPgSimulatorController가 /test-pg/api/v1/payments 경로로 매핑되어 PG 역할 수행


	@BeforeEach
	void setUp() throws Exception {
		TestPgSimulatorController.reset();
		resetPgCircuitBreakers();

		// 사용자 + 브랜드 + 상품 + 주문 생성
		signUpUser("payuser", TEST_PASSWORD, "결제유저", LocalDate.of(1990, 1, 15), "pay@example.com");
		Long brandId = createBrandAndGetId("나이키", "스포츠 브랜드");
		Long productId = createProductAndGetId(brandId, "에어맥스", new BigDecimal("10000"), 100L, "러닝화");
		Long cartItemId = addCartItemAndGetId("payuser", TEST_PASSWORD, productId, 2L);
		orderId = createOrderAndGetId("payuser", TEST_PASSWORD, List.of(cartItemId), "req-pay-001");
	}


	@AfterEach
	void tearDown() {
		TestPgSimulatorController.reset();
		resetPgCircuitBreakers();
		databaseCleanUp.truncateAllTables();
		// Redis entry-token / order-lock 정리
		java.util.Set<String> tokenKeys = redisTemplate.keys("entry-token:*");
		if (tokenKeys != null && !tokenKeys.isEmpty()) redisTemplate.delete(tokenKeys);
		java.util.Set<String> lockKeys = redisTemplate.keys("order-lock:*");
		if (lockKeys != null && !lockKeys.isEmpty()) redisTemplate.delete(lockKeys);
	}


	@Nested
	@DisplayName("POST /api/v1/payments - 결제 요청")
	class RequestPaymentTest {

		@Test
		@DisplayName("[POST /api/v1/payments] PG 성공 -> 201 Created. Payment REQUESTED, transactionKey 포함, 카드번호 마스킹")
		void requestPaymentSuccess() throws Exception {
			// Arrange
			TestPgSimulatorController.setNextBehavior(TestPgSimulatorController.PgBehavior.SUCCESS);
			PaymentCreateRequest request = new PaymentCreateRequest(orderId, "SAMSUNG", "1234-5678-9012-3456");

			// Act & Assert
			mockMvc.perform(post("/api/v1/payments")
					.header(USER_LOGIN_ID_HEADER, "payuser")
					.header(USER_LOGIN_PW_HEADER, TEST_PASSWORD)
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").isNumber())
				.andExpect(jsonPath("$.orderId").value(orderId))
				.andExpect(jsonPath("$.status").value("REQUESTED"))
				.andExpect(jsonPath("$.transactionKey").value(notNullValue()))
				.andExpect(jsonPath("$.cardNo").value("1234-****-****-3456"));
		}


		@Test
		@DisplayName("[POST /api/v1/payments] PG HTTP 500 (Retry 소진) -> 502 PG_REQUEST_FAILED. Payment FAILED")
		void requestPaymentPgFail500() throws Exception {
			// Arrange — 3회 모두 500
			TestPgSimulatorController.setStickyBehavior(TestPgSimulatorController.PgBehavior.FAIL_500);
			PaymentCreateRequest request = new PaymentCreateRequest(orderId, "SAMSUNG", "1234-5678-9012-3456");

			// Act & Assert
			mockMvc.perform(post("/api/v1/payments")
					.header(USER_LOGIN_ID_HEADER, "payuser")
					.header(USER_LOGIN_PW_HEADER, TEST_PASSWORD)
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isBadGateway())
				.andExpect(jsonPath("$.code").value(ErrorType.PG_REQUEST_FAILED.getCode()));

			// 주문 상태 확인: PAYMENT_FAILED
			mockMvc.perform(get("/api/v1/orders/{orderId}", orderId)
					.header(USER_LOGIN_ID_HEADER, "payuser")
					.header(USER_LOGIN_PW_HEADER, TEST_PASSWORD))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("PAYMENT_FAILED"));
		}


		@Test
		@DisplayName("[POST /api/v1/payments] 인증 헤더 누락 -> 401 Unauthorized")
		void requestPaymentUnauthorized() throws Exception {
			// Arrange
			PaymentCreateRequest request = new PaymentCreateRequest(orderId, "SAMSUNG", "1234-5678-9012-3456");

			// Act & Assert
			mockMvc.perform(post("/api/v1/payments")
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value(ErrorType.AUTHENTICATION_FAILED.getCode()));
		}


		@Test
		@DisplayName("[POST /api/v1/payments] 잘못된 카드번호 형식 -> 400 INVALID_CARD_NO")
		void requestPaymentInvalidCardNo() throws Exception {
			// Arrange
			PaymentCreateRequest request = new PaymentCreateRequest(orderId, "SAMSUNG", "invalid-card");

			// Act & Assert
			mockMvc.perform(post("/api/v1/payments")
					.header(USER_LOGIN_ID_HEADER, "payuser")
					.header(USER_LOGIN_PW_HEADER, TEST_PASSWORD)
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value(ErrorType.INVALID_CARD_NO.getCode()));
		}


		@Test
		@DisplayName("[POST /api/v1/payments] 잘못된 카드 타입 -> 400 INVALID_CARD_TYPE")
		void requestPaymentInvalidCardType() throws Exception {
			// Arrange
			PaymentCreateRequest request = new PaymentCreateRequest(orderId, "INVALID", "1234-5678-9012-3456");

			// Act & Assert
			mockMvc.perform(post("/api/v1/payments")
					.header(USER_LOGIN_ID_HEADER, "payuser")
					.header(USER_LOGIN_PW_HEADER, TEST_PASSWORD)
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value(ErrorType.INVALID_CARD_TYPE.getCode()));
		}


		@Test
		@DisplayName("[POST /api/v1/payments] 존재하지 않는 주문 -> 404 ORDER_NOT_FOUND")
		void requestPaymentOrderNotFound() throws Exception {
			// Arrange
			PaymentCreateRequest request = new PaymentCreateRequest(999999L, "SAMSUNG", "1234-5678-9012-3456");

			// Act & Assert
			mockMvc.perform(post("/api/v1/payments")
					.header(USER_LOGIN_ID_HEADER, "payuser")
					.header(USER_LOGIN_PW_HEADER, TEST_PASSWORD)
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value(ErrorType.ORDER_NOT_FOUND.getCode()));
		}


		@Test
		@DisplayName("[POST /api/v1/payments] 이미 REQUESTED 결제 존재 -> 409 PAYMENT_ALREADY_IN_PROGRESS")
		void requestPaymentDuplicate() throws Exception {
			// Arrange — 첫 결제 성공
			TestPgSimulatorController.setNextBehavior(TestPgSimulatorController.PgBehavior.SUCCESS);
			PaymentCreateRequest request = new PaymentCreateRequest(orderId, "SAMSUNG", "1234-5678-9012-3456");

			mockMvc.perform(post("/api/v1/payments")
					.header(USER_LOGIN_ID_HEADER, "payuser")
					.header(USER_LOGIN_PW_HEADER, TEST_PASSWORD)
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isCreated());

			// Act — 동일 주문 재결제 시도
			mockMvc.perform(post("/api/v1/payments")
					.header(USER_LOGIN_ID_HEADER, "payuser")
					.header(USER_LOGIN_PW_HEADER, TEST_PASSWORD)
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value(ErrorType.PAYMENT_ALREADY_IN_PROGRESS.getCode()));
		}

	}


	@Nested
	@DisplayName("POST /api/v1/payments/callback - PG 콜백")
	class CallbackTest {

		@Test
		@DisplayName("[콜백] SUCCESS 콜백 -> 200 OK. Payment SUCCESS, Order PAID")
		void callbackSuccess() throws Exception {
			// Arrange — 결제 생성
			TestPgSimulatorController.setNextBehavior(TestPgSimulatorController.PgBehavior.SUCCESS);
			PaymentCreateRequest request = new PaymentCreateRequest(orderId, "SAMSUNG", "1234-5678-9012-3456");
			MvcResult payResult = mockMvc.perform(post("/api/v1/payments")
					.header(USER_LOGIN_ID_HEADER, "payuser")
					.header(USER_LOGIN_PW_HEADER, TEST_PASSWORD)
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isCreated())
				.andReturn();

			String txKey = objectMapper.readTree(payResult.getResponse().getContentAsString()).get("transactionKey").asText();

			// Act — SUCCESS 콜백
			mockMvc.perform(post("/api/v1/payments/callback")
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(Map.of(
						"transactionKey", txKey,
						"orderId", String.valueOf(orderId),
						"cardType", "SAMSUNG",
						"cardNo", "1234-5678-9012-3456",
						"amount", 20000,
						"status", "SUCCESS",
						"reason", "정상 승인되었습니다."
					))))
				.andExpect(status().isOk());

			// Assert — 주문 상태 PAID 확인
			mockMvc.perform(get("/api/v1/orders/{orderId}", orderId)
					.header(USER_LOGIN_ID_HEADER, "payuser")
					.header(USER_LOGIN_PW_HEADER, TEST_PASSWORD))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("PAID"));
		}


		@Test
		@DisplayName("[콜백] FAILED 콜백 -> 200 OK. Payment FAILED, Order PAYMENT_FAILED")
		void callbackFailed() throws Exception {
			// Arrange — 결제 생성
			TestPgSimulatorController.setNextBehavior(TestPgSimulatorController.PgBehavior.SUCCESS);
			PaymentCreateRequest request = new PaymentCreateRequest(orderId, "SAMSUNG", "1234-5678-9012-3456");
			MvcResult payResult = mockMvc.perform(post("/api/v1/payments")
					.header(USER_LOGIN_ID_HEADER, "payuser")
					.header(USER_LOGIN_PW_HEADER, TEST_PASSWORD)
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isCreated())
				.andReturn();

			String txKey = objectMapper.readTree(payResult.getResponse().getContentAsString()).get("transactionKey").asText();

			// Act — FAILED 콜백
			mockMvc.perform(post("/api/v1/payments/callback")
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(Map.of(
						"transactionKey", txKey,
						"orderId", String.valueOf(orderId),
						"cardType", "SAMSUNG",
						"cardNo", "1234-5678-9012-3456",
						"amount", 20000,
						"status", "FAILED",
						"reason", "한도초과입니다."
					))))
				.andExpect(status().isOk());

			// Assert — 주문 상태 PAYMENT_FAILED
			mockMvc.perform(get("/api/v1/orders/{orderId}", orderId)
					.header(USER_LOGIN_ID_HEADER, "payuser")
					.header(USER_LOGIN_PW_HEADER, TEST_PASSWORD))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("PAYMENT_FAILED"));
		}


		@Test
		@DisplayName("[콜백] 중복 콜백 -> 200 OK. 이미 SUCCESS인 Payment는 skip (멱등)")
		void callbackDuplicate() throws Exception {
			// Arrange — 결제 생성 + SUCCESS 콜백
			TestPgSimulatorController.setNextBehavior(TestPgSimulatorController.PgBehavior.SUCCESS);
			PaymentCreateRequest request = new PaymentCreateRequest(orderId, "SAMSUNG", "1234-5678-9012-3456");
			MvcResult payResult = mockMvc.perform(post("/api/v1/payments")
					.header(USER_LOGIN_ID_HEADER, "payuser")
					.header(USER_LOGIN_PW_HEADER, TEST_PASSWORD)
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isCreated())
				.andReturn();

			String txKey = objectMapper.readTree(payResult.getResponse().getContentAsString()).get("transactionKey").asText();

			String callbackBody = objectMapper.writeValueAsString(Map.of(
				"transactionKey", txKey, "orderId", String.valueOf(orderId),
				"cardType", "SAMSUNG", "cardNo", "1234-5678-9012-3456",
				"amount", 20000, "status", "SUCCESS", "reason", "정상 승인"
			));

			// 1차 콜백
			mockMvc.perform(post("/api/v1/payments/callback")
					.contentType(MediaType.APPLICATION_JSON).content(callbackBody))
				.andExpect(status().isOk());

			// Act — 2차 중복 콜백
			mockMvc.perform(post("/api/v1/payments/callback")
					.contentType(MediaType.APPLICATION_JSON).content(callbackBody))
				.andExpect(status().isOk());

			// Assert — 여전히 PAID
			mockMvc.perform(get("/api/v1/orders/{orderId}", orderId)
					.header(USER_LOGIN_ID_HEADER, "payuser")
					.header(USER_LOGIN_PW_HEADER, TEST_PASSWORD))
				.andExpect(jsonPath("$.status").value("PAID"));
		}


		@Test
		@DisplayName("[콜백] 존재하지 않는 transactionKey -> 404 PAYMENT_NOT_FOUND")
		void callbackNotFound() throws Exception {
			// Act & Assert
			mockMvc.perform(post("/api/v1/payments/callback")
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(Map.of(
						"transactionKey", "nonexistent",
						"orderId", "1", "cardType", "SAMSUNG", "cardNo", "1234-5678-9012-3456",
						"amount", 20000, "status", "SUCCESS", "reason", "정상 승인"
					))))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value(ErrorType.PAYMENT_NOT_FOUND.getCode()));
		}

	}


	@Nested
	@DisplayName("결제 실패 → 재결제 시나리오")
	class RetryPaymentTest {

		@Test
		@DisplayName("[재결제] PG 실패 -> PAYMENT_FAILED -> 다른 카드로 재결제 -> 성공. Order PENDING_PAYMENT → PAID")
		void retryAfterFailure() throws Exception {
			// 1. 첫 결제 실패 (PG 500)
			TestPgSimulatorController.setStickyBehavior(TestPgSimulatorController.PgBehavior.FAIL_500);
			mockMvc.perform(post("/api/v1/payments")
					.header(USER_LOGIN_ID_HEADER, "payuser")
					.header(USER_LOGIN_PW_HEADER, TEST_PASSWORD)
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(new PaymentCreateRequest(orderId, "SAMSUNG", "1234-5678-9012-3456"))))
				.andExpect(status().isBadGateway());

			// Order PAYMENT_FAILED 확인
			mockMvc.perform(get("/api/v1/orders/{orderId}", orderId)
					.header(USER_LOGIN_ID_HEADER, "payuser")
					.header(USER_LOGIN_PW_HEADER, TEST_PASSWORD))
				.andExpect(jsonPath("$.status").value("PAYMENT_FAILED"));

			// 2. 재결제 (다른 카드) — PG 성공
			TestPgSimulatorController.setNextBehavior(TestPgSimulatorController.PgBehavior.SUCCESS);
			MvcResult retryResult = mockMvc.perform(post("/api/v1/payments")
					.header(USER_LOGIN_ID_HEADER, "payuser")
					.header(USER_LOGIN_PW_HEADER, TEST_PASSWORD)
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(new PaymentCreateRequest(orderId, "KB", "9999-8888-7777-6666"))))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.status").value("REQUESTED"))
				.andReturn();

			// 3. SUCCESS 콜백
			String txKey = objectMapper.readTree(retryResult.getResponse().getContentAsString()).get("transactionKey").asText();
			mockMvc.perform(post("/api/v1/payments/callback")
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(Map.of(
						"transactionKey", txKey, "orderId", String.valueOf(orderId),
						"cardType", "KB", "cardNo", "9999-8888-7777-6666",
						"amount", 20000, "status", "SUCCESS", "reason", "정상 승인"
					))))
				.andExpect(status().isOk());

			// Assert — 최종 Order PAID
			mockMvc.perform(get("/api/v1/orders/{orderId}", orderId)
					.header(USER_LOGIN_ID_HEADER, "payuser")
					.header(USER_LOGIN_PW_HEADER, TEST_PASSWORD))
				.andExpect(jsonPath("$.status").value("PAID"));
		}

	}


	@Nested
	@DisplayName("Read Timeout / 수동 복구 시나리오")
	class TimeoutAndRecoveryTest {

		@Test
		@DisplayName("[POST /api/v1/payments] Read timeout -> 504 PG_TIMEOUT. Payment REQUESTED 유지")
		void requestPaymentReadTimeout() throws Exception {
			// Arrange
			TestPgSimulatorController.setNextBehavior(TestPgSimulatorController.PgBehavior.READ_TIMEOUT);
			PaymentCreateRequest request = new PaymentCreateRequest(orderId, "SAMSUNG", "1234-5678-9012-3456");

			// Act & Assert — 504 PG_TIMEOUT
			mockMvc.perform(post("/api/v1/payments")
					.header(USER_LOGIN_ID_HEADER, "payuser")
					.header(USER_LOGIN_PW_HEADER, TEST_PASSWORD)
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isGatewayTimeout())
				.andExpect(jsonPath("$.code").value(ErrorType.PG_TIMEOUT.getCode()));

			// 주문 상태는 여전히 PENDING_PAYMENT (FAILED가 아님)
			mockMvc.perform(get("/api/v1/orders/{orderId}", orderId)
					.header(USER_LOGIN_ID_HEADER, "payuser")
					.header(USER_LOGIN_PW_HEADER, TEST_PASSWORD))
				.andExpect(jsonPath("$.status").value("PENDING_PAYMENT"));
		}


		@Test
		@DisplayName("[POST /api-admin/v1/payments/{id}/recover] Read timeout 이후 수동 복구 -> 200 OK, Payment SUCCESS, Order PAID")
		void recoverPaymentWithAdminAuth() throws Exception {
			// Arrange — Read timeout 발생
			TestPgSimulatorController.setNextBehavior(TestPgSimulatorController.PgBehavior.READ_TIMEOUT);
			mockMvc.perform(post("/api/v1/payments")
					.header(USER_LOGIN_ID_HEADER, "payuser")
					.header(USER_LOGIN_PW_HEADER, TEST_PASSWORD)
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(new PaymentCreateRequest(orderId, "SAMSUNG", "1234-5678-9012-3456"))))
				.andExpect(status().isGatewayTimeout())
				.andExpect(jsonPath("$.code").value(ErrorType.PG_TIMEOUT.getCode()));

			Payment requestedPayment = paymentQueryService.findByOrderIdAndStatus(orderId, PaymentStatus.REQUESTED)
				.orElseThrow();
			waitUntilPgTransactionRegistered(requestedPayment.getPgOrderId());
			Long paymentId = requestedPayment.getId();

			// Act — 관리자 수동 복구
			mockMvc.perform(post("/api-admin/v1/payments/{paymentId}/recover", paymentId)
					.header(ADMIN_LDAP_HEADER, ADMIN_LDAP_VALUE))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(paymentId))
				.andExpect(jsonPath("$.status").value("SUCCESS"))
				.andExpect(jsonPath("$.transactionKey").value(notNullValue()));

			// Assert — 주문 상태도 PAID로 동기화
			mockMvc.perform(get("/api/v1/orders/{orderId}", orderId)
					.header(USER_LOGIN_ID_HEADER, "payuser")
					.header(USER_LOGIN_PW_HEADER, TEST_PASSWORD))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("PAID"));
		}


		@Test
		@DisplayName("[POST /api-admin/v1/payments/{id}/recover] LDAP 헤더 누락 -> 401 Unauthorized")
		void recoverPaymentUnauthorized() throws Exception {
			// Act & Assert
			mockMvc.perform(post("/api-admin/v1/payments/{paymentId}/recover", 1L))
				.andExpect(status().isUnauthorized());
		}

	}


	@Nested
	@DisplayName("주문 상태 조회 — status 필드 노출 확인")
	class OrderStatusVisibilityTest {

		@Test
		@DisplayName("[GET /api/v1/orders] 주문 목록에 status=PENDING_PAYMENT 포함")
		void orderListIncludesStatus() throws Exception {
			// Act & Assert
			mockMvc.perform(get("/api/v1/orders")
					.header(USER_LOGIN_ID_HEADER, "payuser")
					.header(USER_LOGIN_PW_HEADER, TEST_PASSWORD))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[0].status").value("PENDING_PAYMENT"));
		}

		@Test
		@DisplayName("[GET /api/v1/orders/{id}] 주문 상세에 status=PENDING_PAYMENT 포함")
		void orderDetailIncludesStatus() throws Exception {
			// Act & Assert
			mockMvc.perform(get("/api/v1/orders/{orderId}", orderId)
					.header(USER_LOGIN_ID_HEADER, "payuser")
					.header(USER_LOGIN_PW_HEADER, TEST_PASSWORD))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("PENDING_PAYMENT"));
		}

	}


	/**
	 * 테스트 헬퍼 메서드
	 */

	private void signUpUser(String loginId, String password, String name, LocalDate birthDate, String email) throws Exception {
		UserSignUpRequest request = new UserSignUpRequest(loginId, password, name, birthDate, email);
		mockMvc.perform(post("/api/v1/users")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)))
			.andExpect(status().isCreated());
	}

	private Long createBrandAndGetId(String name, String description) throws Exception {
		AdminBrandCreateRequest request = new AdminBrandCreateRequest(name, description);
		MvcResult result = mockMvc.perform(post("/api-admin/v1/brands")
				.header(ADMIN_LDAP_HEADER, ADMIN_LDAP_VALUE)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)))
			.andExpect(status().isCreated()).andReturn();
		return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
	}

	private Long createProductAndGetId(Long brandId, String name, BigDecimal price, Long stock, String description) throws Exception {
		AdminProductCreateRequest request = new AdminProductCreateRequest(brandId, name, price, stock, description);
		MvcResult result = mockMvc.perform(post("/api-admin/v1/products")
				.header(ADMIN_LDAP_HEADER, ADMIN_LDAP_VALUE)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)))
			.andExpect(status().isCreated()).andReturn();
		return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
	}

	private Long addCartItemAndGetId(String loginId, String password, Long productId, Long quantity) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/cart/items")
				.header(USER_LOGIN_ID_HEADER, loginId)
				.header(USER_LOGIN_PW_HEADER, password)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("productId", productId, "quantity", quantity))))
			.andExpect(status().isCreated()).andReturn();
		return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
	}

	private Long createOrderAndGetId(String loginId, String password, List<Long> cartItemIds, String requestId) throws Exception {
		// 주문 생성에 필요한 entry token 셋업
		String entryToken = setupEntryToken(loginId, password);
		OrderCreateRequest request = new OrderCreateRequest(cartItemIds, requestId, null);
		MvcResult result = mockMvc.perform(post("/api/v1/orders")
				.header(USER_LOGIN_ID_HEADER, loginId)
				.header(USER_LOGIN_PW_HEADER, password)
				.header("X-Entry-Token", entryToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)))
			.andExpect(status().isCreated()).andReturn();
		return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
	}

	private String setupEntryToken(String loginId, String password) {
		Long userId = userJpaRepository.findByLoginIdValueAndDeletedAtIsNull(loginId).orElseThrow().getId();
		String token = "entry-" + java.util.UUID.randomUUID();
		redisTemplate.opsForValue().set("entry-token:" + userId, token);
		return token;
	}

	private void waitUntilPgTransactionRegistered(String pgOrderId) throws InterruptedException {
		for (int i = 0; i < 10; i++) {
			if (TestPgSimulatorController.getTransactionKeyByOrderId(pgOrderId) != null) {
				return;
			}
			Thread.sleep(300);
		}
		throw new AssertionError("PG transaction was not registered for pgOrderId=" + pgOrderId);
	}

	private void resetPgCircuitBreakers() {
		circuitBreakerRegistry.circuitBreaker("pgCircuitBreaker").reset();
		circuitBreakerRegistry.circuitBreaker("pgQueryCircuitBreaker").reset();
	}

}
