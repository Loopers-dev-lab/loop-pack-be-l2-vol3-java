package com.loopers.domain.outbox;

/**
 * Outbox Relay 메트릭 추상화.
 *
 * <p>Batch 레이어가 Infrastructure의 Micrometer 구현에 의존하지 않도록
 * 도메인 레이어에 인터페이스를 정의한다.</p>
 *
 * <p>타이머는 {@code Object} 타입의 토큰으로 추상화하여
 * Micrometer {@code Timer.Sample} 의존을 제거한다.</p>
 */
public interface OutboxRelayMetrics {

    /**
     * Outbox 이벤트 발행 성공을 기록한다.
     */
    void recordPublishSuccess();

    /**
     * Outbox 이벤트 발행 실패를 기록한다.
     */
    void recordPublishFail();

    /**
     * Relay 실행 타이머를 시작한다.
     *
     * @return 타이머 토큰 (stopRelayTimer에 전달)
     */
    Object startRelayTimer();

    /**
     * Relay 실행 타이머를 종료한다.
     *
     * @param timerToken startRelayTimer에서 반환된 토큰
     */
    void stopRelayTimer(Object timerToken);
}
