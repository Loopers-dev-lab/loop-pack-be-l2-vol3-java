package com.loopers.interfaces.api.user.v1;

import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import com.loopers.interfaces.api.ApiResponse;

public class UserSteps {

    private static final String SIGNUP_ENDPOINT = "/api/v1/users";

    public static ResponseEntity<ApiResponse<UserV1Dto.SignUpResponse>> signUp(
            TestRestTemplate testRestTemplate,
            UserV1Dto.SignUpRequest request
    ) {
        ParameterizedTypeReference<ApiResponse<UserV1Dto.SignUpResponse>> responseType = new ParameterizedTypeReference<>() {
        };
        return testRestTemplate.exchange(
                SIGNUP_ENDPOINT,
                HttpMethod.POST,
                new HttpEntity<>(request),
                responseType
        );
    }
}
