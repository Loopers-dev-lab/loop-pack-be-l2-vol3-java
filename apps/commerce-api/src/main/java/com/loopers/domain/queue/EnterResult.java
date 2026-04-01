package com.loopers.domain.queue;

/**
 * 대기열 진입 결과. Controller에서 HTTP 상태 코드를 결정하는 데 사용.
 *
 * @param position 대기열 순번 정보
 * @param isNew    true=신규 진입(201), false=이미 대기 중(200)
 */
public record EnterResult(QueuePosition position, boolean isNew) {
}
