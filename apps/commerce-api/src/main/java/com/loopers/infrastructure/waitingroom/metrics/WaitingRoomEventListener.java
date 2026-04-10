package com.loopers.infrastructure.waitingroom.metrics;

import static com.loopers.infrastructure.waitingroom.metrics.WaitingRoomMetricNames.ADMIT_TOTAL;
import static com.loopers.infrastructure.waitingroom.metrics.WaitingRoomMetricNames.CANCEL_TOTAL;
import static com.loopers.infrastructure.waitingroom.metrics.WaitingRoomMetricNames.ENTER_TOTAL;
import static com.loopers.infrastructure.waitingroom.metrics.WaitingRoomMetricNames.TOKEN_COMPLETED_TOTAL;

import com.loopers.domain.waitingroom.event.WaitingRoomAdmittedEvent;
import com.loopers.domain.waitingroom.event.WaitingRoomCancelledEvent;
import com.loopers.domain.waitingroom.event.WaitingRoomCompletedEvent;
import com.loopers.domain.waitingroom.event.WaitingRoomEnteredEvent;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 대기열 도메인 이벤트를 수신하여 Counter 메트릭을 기록한다.
 */
@Component
@RequiredArgsConstructor
public class WaitingRoomEventListener {

    private final MeterRegistry registry;

    /** (1) 유저가 대기열에 진입 — 진입 누적 횟수 */
    @EventListener
    public void onEntered(WaitingRoomEnteredEvent event) {
        registry.counter(ENTER_TOTAL).increment();
    }

    /** (3)(4) 스케줄러가 N명 입장 처리 — 입장 누적 인원 */
    @EventListener
    public void onAdmitted(WaitingRoomAdmittedEvent event) {
        registry.counter(ADMIT_TOTAL).increment(event.admittedCount());
    }

    /** 대기열 취소 — 취소 누적 횟수 */
    @EventListener
    public void onCancelled(WaitingRoomCancelledEvent event) {
        registry.counter(CANCEL_TOTAL).increment();
    }

    /** 주문 완료 — 토큰 정상 소진 누적 */
    @EventListener
    public void onCompleted(WaitingRoomCompletedEvent event) {
        registry.counter(TOKEN_COMPLETED_TOTAL).increment();
    }
}
