package com.loopers.application.useraction;

import com.loopers.domain.useraction.UserActionEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 유저 행동 이벤트 리스너.
 *
 * 구조화된 로그를 남겨 ELK/Datadog 등에서 분석 데이터로 활용한다.
 * Phase 2에서 Kafka Producer로 전환하면, 이 리스너 대신 Kafka를 통해
 * product_metrics 등 외부 시스템에 데이터를 전달하게 된다.
 */
@Slf4j
@Component
public class UserActionEventListener {

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleUserAction(UserActionEvent event) {
        log.info("[유저 행동] action={}, userId={}, targetType={}, targetId={}, metadata={}",
                event.actionType(), event.userId(), event.targetType(), event.targetId(), event.metadata());
    }
}
