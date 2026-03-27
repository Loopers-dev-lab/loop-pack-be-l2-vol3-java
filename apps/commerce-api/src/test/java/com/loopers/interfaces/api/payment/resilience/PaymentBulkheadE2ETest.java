package com.loopers.interfaces.api.payment.resilience;

import com.loopers.domain.payment.CardType;
import com.loopers.domain.payment.gateway.PgType;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.order.OrderRequest;
import com.loopers.interfaces.api.payment.PaymentRequest;
import com.loopers.interfaces.api.payment.PaymentV1Dto;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentBulkheadE2ETest extends PaymentResilienceTestBase {

    @Nested
    class 동시성_제한 {

        @Test
        void Bulkhead_동시_초과시_즉시_거절된다() {
            // maxConcurrentCalls: 20, maxWaitDuration: 0 (즉시 거절)
            // 10초 지연으로 20개 슬롯을 점유, 나머지는 즉시 거절
            setChaosToss("SLOW", "slowMinMs=10000&slowMaxMs=10000");

            int totalRequests = 25;

            // 주문 미리 생성
            List<Long> orderIds = new ArrayList<>();
            for (int i = 0; i < totalRequests; i++) {
                orderIds.add(fixture.placeOrder(
                        List.of(new OrderRequest.PlaceItem(productId, 1)),
                        LOGIN_ID, PASSWORD));
            }

            // 동시 요청
            ExecutorService executor = Executors.newFixedThreadPool(totalRequests);
            List<CompletableFuture<ResponseEntity<ApiResponse<PaymentV1Dto.PaymentResponse>>>> futures =
                    orderIds.stream()
                            .map(orderId -> CompletableFuture.supplyAsync(() -> {
                                PaymentRequest.Request request = new PaymentRequest.Request(
                                        orderId, CardType.SAMSUNG, "1234-5678-9012-3456", PgType.TOSS);
                                return testRestTemplate.exchange(
                                        PAYMENT_ENDPOINT, HttpMethod.POST,
                                        new HttpEntity<>(request, fixture.userHeaders(LOGIN_ID, PASSWORD)),
                                        new ParameterizedTypeReference<ApiResponse<PaymentV1Dto.PaymentResponse>>() {}
                                );
                            }, executor))
                            .toList();

            List<ResponseEntity<ApiResponse<PaymentV1Dto.PaymentResponse>>> responses = futures.stream()
                    .map(CompletableFuture::join)
                    .toList();

            executor.shutdown();

            long rejectedCount = responses.stream()
                    .filter(r -> r.getStatusCode() == HttpStatus.INTERNAL_SERVER_ERROR)
                    .filter(r -> r.getBody() != null && r.getBody().meta() != null
                            && r.getBody().meta().message() != null
                            && r.getBody().meta().message().contains("결제 요청이 많습니다"))
                    .count();

            // 타이밍에 민감하므로 "최소 1건 이상 거절"로 검증
            assertThat(rejectedCount).as("Bulkhead 초과로 거절된 요청이 1건 이상이어야 한다").isGreaterThan(0);
        }
    }
}
