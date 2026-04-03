package com.loopers.application.queue;

import com.loopers.domain.common.event.OrderCompletedEvent;
import com.loopers.domain.queue.QueueService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 주문 완료 시 대기열 토큰 정리 — best-effort
 *
 * OrderFacade가 QueueService를 직접 호출하지 않고,
 * OrderCompletedEvent를 발행하면 이 리스너가 토큰 삭제를 수행한다.
 *
 * 주문 도메인이 큐의 존재를 모르게 하기 위한 설계:
 *   - 토큰 검증: QueueTokenInterceptor → QueueTokenValidator (Interfaces → Application)
 *   - 토큰 삭제: OrderCompletedEvent → QueueTokenCleanupListener (이벤트 기반)
 *   - 두 작업 모두 Queue 도메인 영역에서 처리, 구조적 일관성 확보
 *
 * 실패해도 주문에 영향 없음:
 *   - TTL(180초)로 자연 만료
 *   - 재주문 시 도메인 불변식(재고/쿠폰/포인트)이 방어
 */
@Component
public class QueueTokenCleanupListener {

    private static final Logger log = LoggerFactory.getLogger(QueueTokenCleanupListener.class);

    private final QueueService queueService;

    public QueueTokenCleanupListener(QueueService queueService) {
        this.queueService = queueService;
    }

    @EventListener
    public void handleOrderCompleted(OrderCompletedEvent event) {
        try {
            queueService.deleteToken(event.userId());
        } catch (Exception e) {
            log.warn("대기열 토큰 삭제 실패 — TTL로 자연 만료 예정 (userId={})", event.userId(), e);
        }
    }
}
