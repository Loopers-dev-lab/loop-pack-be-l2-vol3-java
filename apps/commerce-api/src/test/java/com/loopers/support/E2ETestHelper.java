package com.loopers.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.loopers.interfaces.api.ApiResponse;
import com.loopers.support.error.ErrorType;

public class E2ETestHelper {

    public static HttpHeaders userAuthHeaders(String loginId, String loginPw) {
        var headers = new HttpHeaders();
        headers.set("X-Loopers-LoginId", loginId);
        headers.set("X-Loopers-LoginPw", loginPw);
        return headers;
    }

    public static HttpHeaders adminAuthHeaders() {
        var headers = new HttpHeaders();
        headers.set("X-Loopers-Ldap", "loopers.admin");
        return headers;
    }

    public static <T> void assertErrorResponse(
            ResponseEntity<ApiResponse<T>> response,
            HttpStatus status,
            ErrorType errorType
    ) {
        assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(status),
                () -> assertThat(response.getBody()).isNotNull(),
                () -> assertThat(response.getBody().meta().errorCode()).isEqualTo(errorType.getCode())
        );
    }
}