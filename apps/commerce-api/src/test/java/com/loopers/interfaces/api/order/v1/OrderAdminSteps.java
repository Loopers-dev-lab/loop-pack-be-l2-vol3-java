package com.loopers.interfaces.api.order.v1;

import static com.loopers.support.E2ETestHelper.adminAuthHeaders;

import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.PageResponse;

public class OrderAdminSteps {

    public static ResponseEntity<ApiResponse<PageResponse<AdminOrderDto.OrderListResponse>>> getOrders(
            TestRestTemplate testRestTemplate,
            String url
    ) {
        ParameterizedTypeReference<ApiResponse<PageResponse<AdminOrderDto.OrderListResponse>>> responseType = new ParameterizedTypeReference<>() {
        };
        return testRestTemplate.exchange(
                url,
                HttpMethod.GET,
                new HttpEntity<>(adminAuthHeaders()),
                responseType
        );
    }

    public static ResponseEntity<ApiResponse<AdminOrderDto.OrderDetailResponse>> getOrder(
            TestRestTemplate testRestTemplate,
            Long orderId
    ) {
        ParameterizedTypeReference<ApiResponse<AdminOrderDto.OrderDetailResponse>> responseType = new ParameterizedTypeReference<>() {
        };
        return testRestTemplate.exchange(
                "/api-admin/v1/orders/" + orderId,
                HttpMethod.GET,
                new HttpEntity<>(adminAuthHeaders()),
                responseType
        );
    }
}
