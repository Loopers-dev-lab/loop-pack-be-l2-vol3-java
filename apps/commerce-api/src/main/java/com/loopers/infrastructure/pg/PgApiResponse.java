package com.loopers.infrastructure.pg;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** pg-simulator의 공통 응답 래퍼 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PgApiResponse<T>(
    T data
) {
}
