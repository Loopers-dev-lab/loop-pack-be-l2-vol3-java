package com.loopers.application.queue;

import com.loopers.domain.order.OrderCreatedEvent;
import com.loopers.domain.queue.EntryTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class QueueOrderEventListener {

    private final EntryTokenRepository entryTokenRepository;

    // 주문 트랜잭션 커밋 확정 후 토큰 삭제 — 주문 실패 시 이 리스너는 실행되지 않음
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleOrderCreated(OrderCreatedEvent event) {
        entryTokenRepository.delete(event.userId());
        log.debug("입장 토큰 삭제 완료: userId={}", event.userId());
    }
}
