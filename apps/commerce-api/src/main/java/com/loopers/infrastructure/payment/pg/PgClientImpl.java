package com.loopers.infrastructure.payment.pg;

import com.loopers.application.payment.pg.PgClient;
import com.loopers.application.payment.pg.PgPaymentDto;
import com.loopers.application.payment.pg.exception.PgBadRequestException;
import com.loopers.application.payment.pg.exception.PgCircuitOpenException;
import com.loopers.application.payment.pg.exception.PgConnectTimeoutException;
import com.loopers.application.payment.pg.exception.PgReadTimeoutException;
import com.loopers.application.payment.pg.exception.PgServerException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.net.SocketTimeoutException;
import java.util.Optional;

@Component
public class PgClientImpl implements PgClient {

    private static final String PAYMENT_PATH = "/api/v1/payments";

    private final RestTemplate restTemplate;
    private final String baseUrl;
    private final String merchantId;

    public PgClientImpl(
            RestTemplate restTemplate,
            @Value("${payment.pg.base-url}") String baseUrl,
            @Value("${payment.pg.merchant-id}") String merchantId
    ) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl;
        this.merchantId = merchantId;
    }

    @CircuitBreaker(name = "pg", fallbackMethod = "requestPaymentFallback")
    @Retry(name = "pg")
    @Override
    public PgPaymentDto.TransactionResponse requestPayment(PgPaymentDto.PaymentRequest request) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-USER-ID", merchantId);
        HttpEntity<PgPaymentDto.PaymentRequest> entity = new HttpEntity<>(request, headers);

        try {
            ResponseEntity<PgApiResponse<PgPaymentDto.TransactionResponse>> response = restTemplate.exchange(
                    baseUrl + PAYMENT_PATH,
                    HttpMethod.POST,
                    entity,
                    new ParameterizedTypeReference<>() {}
            );
            return response.getBody().data();
        } catch (HttpClientErrorException e) {
            throw new PgBadRequestException("PG 결제 요청이 잘못되었습니다.");
        } catch (HttpServerErrorException e) {
            throw new PgServerException("PG 서버 오류가 발생했습니다.");
        } catch (ResourceAccessException e) {
            if (e.getCause() instanceof SocketTimeoutException ste && ste.getMessage().contains("connect timed out")) {
                throw new PgConnectTimeoutException("PG 서버에 연결할 수 없습니다.");
            }
            throw new PgReadTimeoutException("PG 서버 응답 시간이 초과되었습니다.");
        }
    }

    private PgPaymentDto.TransactionResponse requestPaymentFallback(PgPaymentDto.PaymentRequest request, Exception e) {
        throw new PgCircuitOpenException("PG 서버에 연결할 수 없습니다.");
    }


    @CircuitBreaker(name = "pg", fallbackMethod = "getTransactionFallback")
    @Override
    public Optional<PgPaymentDto.TransactionResponse> getTransaction(String transactionKey) {
        HttpEntity<Void> entity = new HttpEntity<>(authHeaders());
        try {
            ResponseEntity<PgApiResponse<PgPaymentDto.TransactionResponse>> response = restTemplate.exchange(
                    baseUrl + PAYMENT_PATH + "/" + transactionKey,
                    HttpMethod.GET,
                    entity,
                    new ParameterizedTypeReference<>() {}
            );
            return Optional.ofNullable(response.getBody().data());
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        } catch (HttpClientErrorException e) {
            throw new PgBadRequestException("PG 트랜잭션 조회 요청이 잘못되었습니다.");
        } catch (HttpServerErrorException e) {
            throw new PgServerException("PG 서버 오류가 발생했습니다.");
        } catch (ResourceAccessException e) {
            throw new PgReadTimeoutException("PG 서버 응답 시간이 초과되었습니다.");
        }
    }

    private Optional<PgPaymentDto.TransactionResponse> getTransactionFallback(String transactionKey, Exception e) {
        throw new PgCircuitOpenException("PG 서버에 연결할 수 없습니다.");
    }

    @CircuitBreaker(name = "pg", fallbackMethod = "getTransactionsByOrderIdFallback")
    @Override
    public Optional<PgPaymentDto.TransactionListResponse> getTransactionsByOrderId(String orderId) {
        HttpEntity<Void> entity = new HttpEntity<>(authHeaders());
        try {
            ResponseEntity<PgApiResponse<PgPaymentDto.TransactionListResponse>> response = restTemplate.exchange(
                    baseUrl + PAYMENT_PATH + "?orderId=" + orderId,
                    HttpMethod.GET,
                    entity,
                    new ParameterizedTypeReference<>() {}
            );
            return Optional.ofNullable(response.getBody().data());
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        } catch (HttpClientErrorException e) {
            throw new PgBadRequestException("PG 트랜잭션 목록 조회 요청이 잘못되었습니다.");
        } catch (HttpServerErrorException e) {
            throw new PgServerException("PG 서버 오류가 발생했습니다.");
        } catch (ResourceAccessException e) {
            throw new PgReadTimeoutException("PG 서버 응답 시간이 초과되었습니다.");
        }
    }

    private Optional<PgPaymentDto.TransactionListResponse> getTransactionsByOrderIdFallback(String orderId, Exception e) {
        throw new PgCircuitOpenException("PG 서버에 연결할 수 없습니다.");
    }

    private HttpHeaders authHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-USER-ID", merchantId);
        return headers;
    }
}
