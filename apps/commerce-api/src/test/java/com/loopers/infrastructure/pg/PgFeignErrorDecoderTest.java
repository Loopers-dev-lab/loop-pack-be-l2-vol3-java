package com.loopers.infrastructure.pg;

import static org.assertj.core.api.Assertions.assertThat;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import feign.Request;
import feign.Response;
import feign.RetryableException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;
import java.util.Collections;

class PgFeignErrorDecoderTest {
    private final PgFeignErrorDecoder decoder = new PgFeignErrorDecoder();

    private Response createResponse(int status) {
        Request request = Request.create(
                Request.HttpMethod.POST, "http://pg/api/v1/payments",
                Collections.emptyMap(), null, StandardCharsets.UTF_8, null
        );
        return Response.builder()
                .status(status)
                .reason("error")
                .request(request)
                .headers(Collections.emptyMap())
                .build();
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 401, 403, 404, 422})
    @DisplayName("4xx 응답 시 CoreException(BAD_REQUEST) 반환 — 재시도 불필요한 클라이언트 오류")
    void clientError_returns_CoreException(int status) {
        // when
        Exception result = decoder.decode("PgFeignClient#requestPayment", createResponse(status));

        // then
        assertThat(result).isInstanceOf(CoreException.class);
        CoreException coreException = (CoreException) result;
        assertThat(coreException.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        assertThat(coreException.getMessage()).contains(String.valueOf(status));
    }

    @ParameterizedTest
    @ValueSource(ints = {500, 502, 503, 504})
    @DisplayName("5xx 응답 시 RetryableException 반환 — Resilience4j 재시도 대상")
    void serverError_returns_RetryableException(int status) {
        // when
        Exception result = decoder.decode("PgFeignClient#requestPayment", createResponse(status));

        // then
        assertThat(result).isInstanceOf(RetryableException.class);
        assertThat(result.getMessage()).contains(String.valueOf(status));
    }

    @Test
    @DisplayName("3xx 등 기타 응답은 기본 ErrorDecoder에 위임")
    void otherError_delegates_to_default() {
        // when
        Exception result = decoder.decode("PgFeignClient#requestPayment", createResponse(301));

        // then — 기본 디코더는 FeignException 계열 반환
        assertThat(result).isNotInstanceOf(CoreException.class);
        assertThat(result).isNotInstanceOf(RetryableException.class);
    }
}
