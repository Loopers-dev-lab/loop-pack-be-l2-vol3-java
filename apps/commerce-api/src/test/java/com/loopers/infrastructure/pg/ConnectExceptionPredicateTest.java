package com.loopers.infrastructure.pg;

import feign.Request;
import feign.RetryableException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

class ConnectExceptionPredicateTest {

    private final ConnectExceptionPredicate predicate = new ConnectExceptionPredicate();

    @Test
    @DisplayName("ConnectException → true: 연결 실패는 재시도 대상")
    void connectException_returnsTrue() {
        ConnectException ex = new ConnectException("Connection refused");
        assertThat(predicate.test(ex)).isTrue();
    }

    @Test
    @DisplayName("SocketTimeoutException → false: 읽기 타임아웃은 재시도 금지")
    void socketTimeoutException_returnsFalse() {
        SocketTimeoutException ex = new SocketTimeoutException("Read timed out");
        assertThat(predicate.test(ex)).isFalse();
    }

    @Test
    @DisplayName("RetryableException(cause=ConnectException) → true: Feign 래핑된 ConnectException도 탐지")
    void retryableExceptionWrappingConnectException_returnsTrue() {
        ConnectException connectEx = new ConnectException("Connection refused");
        Request dummyRequest = Request.create(
                Request.HttpMethod.POST, "http://localhost", Collections.emptyMap(), null, null, null
        );
        RetryableException retryableEx = new RetryableException(
                -1, "connect failed", Request.HttpMethod.POST, connectEx, (Long) null, dummyRequest
        );
        assertThat(predicate.test(retryableEx)).isTrue();
    }
}
