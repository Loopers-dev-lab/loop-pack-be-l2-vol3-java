package com.loopers.domain.metrics;

/**
 * 상품 지표 이벤트 처리 전략 인터페이스.
 *
 * <p>각 구현체는 특정 {@link MetricsEventType}을 지원하며,
 * 해당 이벤트의 {@link MetricsPayload}를 받아 지표를 갱신한다.</p>
 */
public interface MetricsEventHandler {

    /**
     * 해당 이벤트 타입을 처리할 수 있는지 반환한다.
     *
     * @param eventType 이벤트 타입
     * @return 처리 가능 여부
     */
    boolean supports(MetricsEventType eventType);

    /**
     * 이벤트 페이로드를 처리하여 상품 지표를 갱신한다.
     *
     * @param payload 이벤트 페이로드
     */
    void handle(MetricsPayload payload);
}
