package com.loopers.infrastructure.payment;

import com.loopers.domain.payment.PgClient;
import com.loopers.domain.payment.PgPaymentRequest;
import com.loopers.domain.payment.PgPaymentResponse;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.ConnectException;
import java.time.Duration;
import java.util.List;

@Slf4j
@Component
public class PgClientImpl implements PgClient {

    private final RestClient restClient;

    public PgClientImpl(@Value("${pg.url:http://localhost:8082}") String pgUrl) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(1));
        factory.setReadTimeout(Duration.ofSeconds(1));

        this.restClient = RestClient.builder()
            .baseUrl(pgUrl)
            .requestFactory(factory)
            .build();
    }

    @CircuitBreaker(name = "pg", fallbackMethod = "fallback")
    @Retry(name = "pg")
    @Override
    public PgPaymentResponse requestPayment(PgPaymentRequest request) {
        try {
            PgApiResponse response = restClient.post()
                .uri("/api/v1/payments")
                .header("X-USER-ID", request.userId())
                .contentType(MediaType.APPLICATION_JSON)
                .body(new PgRequestBody(
                    request.orderId(),
                    request.cardType(),
                    request.cardNo(),
                    request.amount(),
                    request.callbackUrl()
                ))
                .retrieve()
                .body(PgApiResponse.class);

            if (response == null || response.data() == null) {
                throw new CoreException(ErrorType.INTERNAL_ERROR, "PG 응답이 비어있습니다");
            }

            return new PgPaymentResponse(
                response.data().transactionKey(),
                response.data().status(),
                response.data().reason()
            );
        } catch (ResourceAccessException e) {
            if (e.getCause() instanceof ConnectException) {
                log.error("PG 연결 실패. request={}", request, e);
                throw new PgConnectionException();
            }
            log.error("PG 타임아웃. request={}", request, e);
            throw new CoreException(ErrorType.INTERNAL_ERROR, "PG 응답 시간이 초과됐습니다.");
        } catch (HttpServerErrorException e) {
            log.error("PG 서버 오류. request={}", request, e);
            throw new PgConnectionException();
        } catch (RestClientException e) {
            log.error("PG 호출 실패. request={}", request, e);
            throw new CoreException(ErrorType.INTERNAL_ERROR, "PG 호출에 실패했습니다.");
        }
    }

    @Override
    public List<PgPaymentResponse> getPaymentsByOrderId(String userId, String orderId) {
        try {
            PgOrderApiResponse response = restClient.get()
                .uri("/api/v1/payments?orderId={orderId}", orderId)
                .header("X-USER-ID", userId)
                .retrieve()
                .body(PgOrderApiResponse.class);

            if (response == null || response.data() == null) {
                return List.of();
            }

            return response.data().transactions().stream()
                .map(t -> new PgPaymentResponse(t.transactionKey(), t.status(), t.reason()))
                .toList();
        } catch (RestClientException e) {
            log.error("PG 결제 조회 실패. orderId={}", orderId, e);
            throw new CoreException(ErrorType.INTERNAL_ERROR, "PG 결제 조회에 실패했습니다.");
        }
    }

    private PgPaymentResponse fallback(PgPaymentRequest request, Throwable t) {
        if (t instanceof CallNotPermittedException) {
            log.error("PG 서킷 브레이커 OPEN. request={}", request, t);
            throw new CoreException(ErrorType.INTERNAL_ERROR, "현재 결제 서비스가 일시적으로 불가합니다.");
        }
        log.error("PG 호출 최종 실패. request={}", request, t);
        throw new CoreException(ErrorType.INTERNAL_ERROR, "PG 호출에 실패했습니다.");
    }

    private record PgRequestBody(
        String orderId,
        String cardType,
        String cardNo,
        Long amount,
        String callbackUrl
    ) {}

    private record PgApiResponse(
        PgMeta meta,
        PgData data
    ) {}

    private record PgMeta(
        String result,
        String errorCode,
        String message
    ) {}

    private record PgData(
        String transactionKey,
        String status,
        String reason
    ) {}

    private record PgOrderApiResponse(
        PgMeta meta,
        PgOrderData data
    ) {}

    private record PgOrderData(
        String orderId,
        List<PgData> transactions
    ) {}
}
