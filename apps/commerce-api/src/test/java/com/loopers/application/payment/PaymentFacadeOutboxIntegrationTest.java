package com.loopers.application.payment;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.domain.brand.Brand;
import com.loopers.domain.common.Money;
import com.loopers.domain.product.Product;
import com.loopers.domain.queue.TokenService;
import com.loopers.domain.stock.Stock;
import com.loopers.infrastructure.brand.BrandJpaRepository;
import com.loopers.infrastructure.outbox.OutboxEventJpaRepository;
import com.loopers.infrastructure.product.ProductJpaRepository;
import com.loopers.infrastructure.stock.StockJpaRepository;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.utils.DatabaseCleanUp;
import com.loopers.utils.RedisCleanUp;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PaymentFacadeOutboxIntegrationTest {

    private static final String USERS_ENDPOINT = "/api/v1/users";
    private static final String ORDERS_ENDPOINT = "/api/v1/orders";
    private static final String PAYMENT_ENDPOINT = "/api/v1/payments";
    private static final String CALLBACK_ENDPOINT = "/api/v1/payments/callback";
    private static final String LOGIN_ID = "rankuser";
    private static final String PASSWORD = "Test1234!";

    @Autowired
    private TestRestTemplate testRestTemplate;

    @Autowired
    private BrandJpaRepository brandJpaRepository;

    @Autowired
    private ProductJpaRepository productJpaRepository;

    @Autowired
    private StockJpaRepository stockJpaRepository;

    @Autowired
    private OutboxEventJpaRepository outboxEventJpaRepository;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @Autowired
    private RedisCleanUp redisCleanUp;

    @BeforeEach
    void setUp() {
        testRestTemplate.exchange(
            USERS_ENDPOINT, HttpMethod.POST,
            new HttpEntity<>(Map.of(
                "loginId", LOGIN_ID, "password", PASSWORD,
                "name", "랭킹유저", "birthDate", "19900101", "email", "rank@test.com"
            )),
            new ParameterizedTypeReference<ApiResponse<Map<String, String>>>() {}
        );
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
        redisCleanUp.truncateAll();
    }

    @DisplayName("결제 콜백 성공 시, PRODUCT_SOLD outbox payload에 amount가 포함된다.")
    @Nested
    class HandleCallback {

        @DisplayName("주문 금액(price * quantity)이 payload의 amount 필드에 담긴다.")
        @Test
        void productSoldPayloadIncludesAmount() throws JsonProcessingException {
            // arrange
            Brand brand = brandJpaRepository.save(new Brand("나이키"));
            long price = 50000L;
            long quantity = 2L;
            Product product = productJpaRepository.save(new Product(brand.getId(), "신발", new Money(price), "설명"));
            stockJpaRepository.save(new Stock(product.getId(), 100L));

            // 대기열 토큰 발급
            String queueToken = tokenService.issue(LOGIN_ID);

            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Loopers-LoginId", LOGIN_ID);
            headers.set("X-Loopers-LoginPw", PASSWORD);
            headers.set("X-Queue-Token", queueToken);
            headers.set("X-User-Id", LOGIN_ID);
            headers.set("Content-Type", "application/json");

            // 주문 생성
            ResponseEntity<ApiResponse<Map<String, Object>>> orderResp = testRestTemplate.exchange(
                ORDERS_ENDPOINT, HttpMethod.POST,
                new HttpEntity<>(Map.of(
                    "items", List.of(Map.of("productId", product.getId(), "quantity", quantity)),
                    "cardType", "SAMSUNG",
                    "cardNo", "1234-5678-9012-3456",
                    "updateDefaultCard", false
                ), headers),
                new ParameterizedTypeReference<>() {}
            );
            Long orderId = ((Number) orderResp.getBody().data().get("orderId")).longValue();

            // 결제 요청
            testRestTemplate.exchange(
                PAYMENT_ENDPOINT, HttpMethod.POST,
                new HttpEntity<>(Map.of("orderId", orderId, "cardType", "SAMSUNG", "cardNo", "1234-5678-9012-3456"), headers),
                new ParameterizedTypeReference<ApiResponse<Map<String, Object>>>() {}
            );

            // act — 결제 성공 콜백
            HttpHeaders callbackHeaders = new HttpHeaders();
            callbackHeaders.set("Content-Type", "application/json");
            testRestTemplate.exchange(
                CALLBACK_ENDPOINT, HttpMethod.POST,
                new HttpEntity<>(Map.of("orderId", String.valueOf(orderId), "transactionId", "TR-001", "success", true), callbackHeaders),
                Void.class
            );

            // assert — PRODUCT_SOLD outbox 이벤트 payload에 amount 포함 여부 검증
            var soldEvents = outboxEventJpaRepository.findAll().stream()
                .filter(e -> e.getEventType().equals("PRODUCT_SOLD"))
                .toList();

            assertThat(soldEvents).isNotEmpty();
            for (var event : soldEvents) {
                Map<?, ?> payload = objectMapper.readValue(event.getPayload(), Map.class);
                assertThat(payload.get("amount")).isNotNull();
                assertThat(((Number) payload.get("amount")).longValue()).isEqualTo(price * quantity);
            }
        }
    }
}
