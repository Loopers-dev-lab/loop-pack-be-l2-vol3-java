package com.loopers.application.observability.handler;

import com.loopers.application.observability.event.BusinessSuccessObservedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
public class BusinessSuccessObservedEventHandler {

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(BusinessSuccessObservedEvent event) {
        log.info(
                "business_success action={}, domain={}, class={}, method={}, traceId={}, memberId={}, aggregateId={}, elapsedMs={}, observedAt={}",
                event.action(),
                event.domain(),
                event.className(),
                event.methodName(),
                event.traceId(),
                event.memberId(),
                event.aggregateId(),
                event.elapsedMs(),
                event.observedAt()
        );
    }
}
