package com.loopers.domain.usercard;

import com.loopers.domain.order.OrderCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@RequiredArgsConstructor
@Component
public class OrderUserCardEventListener {

    private final UserCardService userCardService;

    /**
     * 주문 생성 커밋 후 비동기로 카드 정보 저장.
     * - 카드 저장 실패 시 주문/결제에 영향 없음 (이벤트 분리)
     * - 실패 시 로그로 추적, 추후 알림으로 확장 가능
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleOrderCreated(OrderCreatedEvent event) {
        try {
            userCardService.saveCard(
                event.memberId(), event.cardType(), event.cardNo(), event.updateDefaultCard()
            );
        } catch (Exception e) {
            log.warn("[카드 저장 실패] 고객 알림 필요. memberId={}, 이유={}", event.memberId(), e.getMessage());
        }
    }
}