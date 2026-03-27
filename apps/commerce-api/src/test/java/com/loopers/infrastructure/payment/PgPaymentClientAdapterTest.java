package com.loopers.infrastructure.payment;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.domain.payment.PgOrderTransactions;
import com.loopers.domain.payment.PgPaymentRequest;
import com.loopers.domain.payment.PgPaymentResult;
import com.loopers.domain.payment.PgRequestStatus;
import com.loopers.domain.payment.PgTransactionDetail;
import feign.Feign;
import feign.Request;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.http.HttpMessageConverters;
import org.springframework.cloud.openfeign.support.SpringDecoder;
import org.springframework.cloud.openfeign.support.SpringEncoder;
import org.springframework.cloud.openfeign.support.SpringMvcContract;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import feign.codec.ErrorDecoder;

@Tag("external")
class PgPaymentClientAdapterTest {

    private static final String PG_BASE_URL = "http://localhost:8082";
    private static final String CALLBACK_URL = "http://localhost:8080/api/v1/payments/callback";
    private static final String TEST_USER_ID = "testuser1";
    private static final String TEST_CARD_TYPE = "SAMSUNG";
    private static final String TEST_CARD_NO = "1234-5678-9814-1451";
    private static final long ASYNC_WAIT_MS = 6000;

    private PgPaymentClientAdapter adapter;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        HttpMessageConverters messageConverters =
                new HttpMessageConverters(
                        new MappingJackson2HttpMessageConverter(objectMapper));

        PgPaymentFeignClient feignClient = Feign.builder()
                .contract(new SpringMvcContract())
                .encoder(new SpringEncoder(() -> messageConverters))
                .decoder(new SpringDecoder(() -> messageConverters))
                .errorDecoder(new PgErrorDecoder(objectMapper))
                .options(new Request.Options(
                        3, TimeUnit.SECONDS, 5, TimeUnit.SECONDS, true))
                .target(PgPaymentFeignClient.class, PG_BASE_URL);

        Pg2PaymentFeignClient pg2Client = Feign.builder()
                .contract(new SpringMvcContract())
                .encoder(new SpringEncoder(() -> messageConverters))
                .decoder(new SpringDecoder(() -> messageConverters))
                .errorDecoder(new PgErrorDecoder(objectMapper))
                .options(new Request.Options(
                        3, TimeUnit.SECONDS, 5, TimeUnit.SECONDS, true))
                .target(Pg2PaymentFeignClient.class, PG_BASE_URL);

        MaintenanceWindowFilter maintenanceWindowFilter = new MaintenanceWindowFilter();
        adapter = new PgPaymentClientAdapter(
                feignClient, pg2Client,
                CircuitBreakerRegistry.ofDefaults(),
                io.github.resilience4j.retry.RetryRegistry.ofDefaults(),
                maintenanceWindowFilter);
    }

    @DisplayName("결제를 요청할 때, ")
    @Nested
    class RequestPayment {

        @DisplayName("PG 응답이 정상적으로 파싱된다.")
        @Test
        void requestPayment_parsesResponse() {
            // arrange
            PgPaymentRequest request = createRequest("TEST_ORDER_001", 50000);

            // act
            PgPaymentResult result = adapter.requestPayment(request);

            // assert
            assertThat(result).isNotNull();
            if (result.requested()) {
                assertThat(result.transactionKey()).isNotBlank();
                assertThat(result.status()).isEqualTo(PgRequestStatus.ACCEPTED);
            } else {
                assertThat(result.transactionKey()).isNull();
                assertThat(result.status()).isIn(
                        PgRequestStatus.SERVER_ERROR,
                        PgRequestStatus.VALIDATION_ERROR,
                        PgRequestStatus.CONNECTION_ERROR);
            }
        }
    }

    @DisplayName("결제 상태를 조회할 때, ")
    @Nested
    class GetPaymentStatus {

        @DisplayName("transactionKey로 상세 정보를 조회한다.")
        @Test
        void getPaymentStatus_returnsDetail() {
            // arrange
            String transactionKey = requestPaymentUntilAccepted(
                    "STATUS_TEST_" + System.nanoTime());
            sleep(ASYNC_WAIT_MS);

            // act
            PgTransactionDetail detail = adapter.getPaymentStatus(
                    transactionKey, TEST_USER_ID);

            // assert
            assertThat(detail).isNotNull();
            assertThat(detail.transactionKey()).isEqualTo(transactionKey);
            assertThat(detail.orderId()).isNotBlank();
            assertThat(detail.status()).isIn("SUCCESS", "FAILED");
        }
    }

    @DisplayName("주문별 결제 목록을 조회할 때, ")
    @Nested
    class GetPaymentsByOrder {

        @DisplayName("orderId로 해당 주문의 모든 트랜잭션을 조회한다.")
        @Test
        void getPaymentsByOrder_returnsList() {
            // arrange
            String orderId = "ORDER_LIST_" + System.nanoTime();
            requestPaymentUntilAccepted(orderId);
            sleep(ASYNC_WAIT_MS);

            // act
            PgOrderTransactions result = adapter.getPaymentsByOrder(
                    orderId, TEST_USER_ID);

            // assert
            assertThat(result).isNotNull();
            assertThat(result.orderId()).isEqualTo(orderId);
            assertThat(result.transactions()).isNotEmpty();
            assertThat(result.transactions().get(0).status())
                    .isIn("SUCCESS", "FAILED");
        }
    }

    private PgPaymentRequest createRequest(String orderId, long amount) {
        return new PgPaymentRequest(
                orderId, TEST_CARD_TYPE, TEST_CARD_NO,
                amount, CALLBACK_URL, TEST_USER_ID);
    }

    private String requestPaymentUntilAccepted(String orderId) {
        for (int i = 0; i < 20; i++) {
            PgPaymentResult result = adapter.requestPayment(
                    createRequest(orderId, 10000));
            if (result.requested()) {
                return result.transactionKey();
            }
        }
        throw new RuntimeException(
                "PG 요청이 20회 연속 실패 — PG 시뮬레이터 상태 확인 필요");
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
