package com.loopers.interfaces.event.queue;

import static com.loopers.support.config.AsyncConfig.EVENT_EXECUTOR;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.loopers.domain.order.OrderEvent;
import com.loopers.support.entry.EntryTokenStore;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 입장 토큰의 이벤트 리스너.
 *
 * <p>주문 성공 후 커밋이 완료되면 해당 유저의 입장 토큰을 삭제한다.
 * TTL 만료 전에 삭제되지 않은 토큰은 주문 미처리 유저로 모니터링된다.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EntryTokenEventListener {

    private final EntryTokenStore entryTokenStore;

    /**
     * 주문 생성 이벤트를 처리한다.
     *
     * <p>트랜잭션 커밋 이후 입장 토큰을 삭제하여 주문 정상 처리를 기록한다.</p>
     *
     * @param event 주문 생성 이벤트
     */
    @Async(EVENT_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(OrderEvent.OrderPlaced event) {
        log.info("[EVENT:OrderPlaced:entryToken] orderId={}, userId={}", event.orderId(), event.userId());
        try {
            entryTokenStore.delete(event.userId());
        } catch (Exception e) {
            log.error("주문 생성 이벤트 처리 실패: 입장 토큰 삭제 [orderId={}, userId={}]", event.orderId(), event.userId(), e);
        }
    }
}
