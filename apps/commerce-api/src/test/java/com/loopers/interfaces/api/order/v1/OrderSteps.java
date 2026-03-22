package com.loopers.interfaces.api.order.v1;

import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.PageResponse;

public class OrderSteps {

    private static final String ORDER_ENDPOINT = "/api/v1/orders";

    public static ResponseEntity<ApiResponse<OrderDto.CreateOrderResponse>> createOrder(
            TestRestTemplate testRestTemplate,
            OrderDto.CreateOrderRequest request,
            HttpHeaders headers
    ) {
        ParameterizedTypeReference<ApiResponse<OrderDto.CreateOrderResponse>> responseType = new ParameterizedTypeReference<>() {
        };
        return testRestTemplate.exchange(
                ORDER_ENDPOINT,
                HttpMethod.POST,
                new HttpEntity<>(request, headers),
                responseType
        );
    }

    public static ResponseEntity<ApiResponse<PageResponse<OrderDto.OrderListResponse>>> getMyOrders(
            TestRestTemplate testRestTemplate,
            String url,
            HttpHeaders headers
    ) {
        ParameterizedTypeReference<ApiResponse<PageResponse<OrderDto.OrderListResponse>>> responseType = new ParameterizedTypeReference<>() {
        };
        return testRestTemplate.exchange(
                url,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                responseType
        );
    }

    public static ResponseEntity<ApiResponse<OrderDto.OrderDetailResponse>> getMyOrder(
            TestRestTemplate testRestTemplate,
            String orderKey,
            HttpHeaders headers
    ) {
        ParameterizedTypeReference<ApiResponse<OrderDto.OrderDetailResponse>> responseType = new ParameterizedTypeReference<>() {
        };
        return testRestTemplate.exchange(
                ORDER_ENDPOINT + "/" + orderKey,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                responseType
        );
    }
}
