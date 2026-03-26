package com.loopers.application.order;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.domain.order.event.OrderCreatedEvent;
import com.loopers.domain.outbox.OutboxEvent;
import com.loopers.domain.outbox.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

import static org.springframework.transaction.event.TransactionPhase.AFTER_COMMIT;
import static org.springframework.transaction.event.TransactionPhase.BEFORE_COMMIT;

@Slf4j
@RequiredArgsConstructor
@Component
public class OrderEventHandler {

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    /**
     * Outbox 저장 - BEFORE_COMMIT이므로 OrderFacade의 TX 안에서 실행된다.
     * 주문 데이터와 같은 TX에 저장되므로, 둘 다 커밋되거나 둘 다 롤백된다.
     */
    @TransactionalEventListener(phase = BEFORE_COMMIT)
    public void saveToOutbox(OrderCreatedEvent event) {
        try {
            String payload = objectMapper.writeValueAsString(event);
            outboxEventRepository.save(new OutboxEvent(
                    "ORDER", event.orderId(), "ORDER_CREATED", payload
            ));
        } catch (Exception e) {
            log.error("Outbox 저장 실패: {}", event, e);
            throw new RuntimeException(e);
        }
    }

    /**
     * 유저 행동 로깅 - AFTER_COMMIT이므로 TX 커밋 후 비동기 실행.
     * 실패해도 주문에 영향 없음.
     */
    @TransactionalEventListener(phase = AFTER_COMMIT)
    @Async
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void handleOrderCreated(OrderCreatedEvent event) {
        log.info("주문 생성 이벤트: orderId={}, memberId={}, totalAmount={}, itemCount={}",
                event.orderId(), event.memberId(), event.totalAmount(), event.itemCount());
    }
}
