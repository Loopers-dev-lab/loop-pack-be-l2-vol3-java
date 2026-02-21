package com.loopers.interfaces.api.like.v1;

import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import com.loopers.interfaces.api.ApiResponse;

public class LikeSteps {

    public static ResponseEntity<ApiResponse<Object>> likeProduct(
            TestRestTemplate testRestTemplate,
            Long productId,
            HttpHeaders headers
    ) {
        ParameterizedTypeReference<ApiResponse<Object>> responseType = new ParameterizedTypeReference<>() {
        };
        return testRestTemplate.exchange(
                "/api/v1/products/" + productId + "/likes",
                HttpMethod.POST,
                new HttpEntity<>(headers),
                responseType
        );
    }
}