package com.loopers.infrastructure.payment;

/**
 * PG 시뮬레이터 API 공통 응답 래퍼.
 * <p>
 * PG API는 {@code {"data": {...}}} 형태로 응답한다.
 * </p>
 *
 * @param <T> 응답 데이터 타입
 */
public record PgApiResponse<T>(T data) {
}
