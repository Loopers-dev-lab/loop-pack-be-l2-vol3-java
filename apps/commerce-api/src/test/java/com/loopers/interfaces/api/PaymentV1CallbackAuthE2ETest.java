package com.loopers.interfaces.api;

import com.loopers.application.payment.PaymentApplicationService;
import com.loopers.interfaces.api.payment.PaymentCallbackStatus;
import com.loopers.interfaces.api.payment.PaymentV1Dto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("PG 콜백 인증 E2E 테스트")
class PaymentV1CallbackAuthE2ETest {

    @Autowired
    private TestRestTemplate testRestTemplate;

    @MockBean
    private PaymentApplicationService paymentApplicationService;

    private static final String CALLBACK_ENDPOINT = "/api/v1/payments/callback";
    private static final String VALID_SECRET = "pg-callback-secret-key";

    private PaymentV1Dto.PaymentCallbackRequest callbackRequest() {
        return new PaymentV1Dto.PaymentCallbackRequest("TR:test123", PaymentCallbackStatus.SUCCESS, "정상 승인");
    }

    @DisplayName("콜백 인증 시, ")
    @Nested
    class CallbackAuth {

        @DisplayName("유효한 X-PG-Signature 헤더이면, 200을 반환한다.")
        @Test
        void returns200_whenValidSignature() {
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-PG-Signature", VALID_SECRET);
            headers.set("Content-Type", "application/json");

            ResponseEntity<Map> response = testRestTemplate.exchange(
                CALLBACK_ENDPOINT, HttpMethod.POST,
                new HttpEntity<>(callbackRequest(), headers),
                Map.class
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @DisplayName("잘못된 X-PG-Signature 헤더이면, 401을 반환한다.")
        @Test
        void returns401_whenInvalidSignature() {
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-PG-Signature", "wrong-secret");
            headers.set("Content-Type", "application/json");

            ResponseEntity<Map> response = testRestTemplate.exchange(
                CALLBACK_ENDPOINT, HttpMethod.POST,
                new HttpEntity<>(callbackRequest(), headers),
                Map.class
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @DisplayName("X-PG-Signature 헤더가 없으면, 401을 반환한다.")
        @Test
        void returns401_whenNoSignature() {
            HttpHeaders headers = new HttpHeaders();
            headers.set("Content-Type", "application/json");

            ResponseEntity<Map> response = testRestTemplate.exchange(
                CALLBACK_ENDPOINT, HttpMethod.POST,
                new HttpEntity<>(callbackRequest(), headers),
                Map.class
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }
}
