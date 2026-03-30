package com.loopers.interfaces.api.queue.v1;

import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import com.loopers.interfaces.api.ApiResponse;

public class QueueSteps {

    public static ResponseEntity<ApiResponse<Object>> enterQueue(
            TestRestTemplate testRestTemplate,
            HttpHeaders headers
    ) {
        ParameterizedTypeReference<ApiResponse<Object>> responseType = new ParameterizedTypeReference<>() {
        };
        return testRestTemplate.exchange(
                "/api/v1/queue/enter",
                HttpMethod.POST,
                new HttpEntity<>(headers),
                responseType
        );
    }
}
