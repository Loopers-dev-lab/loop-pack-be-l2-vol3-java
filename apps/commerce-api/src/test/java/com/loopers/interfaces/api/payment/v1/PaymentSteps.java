package com.loopers.interfaces.api.payment.v1;

import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import com.loopers.interfaces.api.ApiResponse;

public class PaymentSteps {

    private static final String PAYMENT_ENDPOINT = "/api/v1/payments";

    public static ResponseEntity<ApiResponse<PaymentDto.CreatePaymentResponse>> createPayment(
            TestRestTemplate testRestTemplate,
            PaymentDto.CreatePaymentRequest request,
            HttpHeaders headers
    ) {
        ParameterizedTypeReference<ApiResponse<PaymentDto.CreatePaymentResponse>> responseType = new ParameterizedTypeReference<>() {
        };
        return testRestTemplate.exchange(
                PAYMENT_ENDPOINT,
                HttpMethod.POST,
                new HttpEntity<>(request, headers),
                responseType
        );
    }

    public static ResponseEntity<ApiResponse<Void>> handlePaymentCallback(
            TestRestTemplate testRestTemplate,
            PaymentDto.PaymentCallbackRequest request
    ) {
        ParameterizedTypeReference<ApiResponse<Void>> responseType = new ParameterizedTypeReference<>() {
        };
        return testRestTemplate.exchange(
                PAYMENT_ENDPOINT + "/callback",
                HttpMethod.POST,
                new HttpEntity<>(request),
                responseType
        );
    }
}
