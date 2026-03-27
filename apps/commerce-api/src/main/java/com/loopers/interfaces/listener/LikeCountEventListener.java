package com.loopers.interfaces.listener;

import com.loopers.domain.event.LikeCreatedEvent;
import com.loopers.domain.event.LikeRemovedEvent;
import com.loopers.domain.product.ProductRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.concurrent.Executor;

@Slf4j
@Component
public class LikeCountEventListener {

    private final ProductRepository productRepository;
    private final Executor eventExecutor;
    private final TransactionTemplate transactionTemplate;

    public LikeCountEventListener(
            ProductRepository productRepository,
            @Qualifier("eventExecutor") Executor eventExecutor,
            PlatformTransactionManager transactionManager) {
        this.productRepository = productRepository;
        this.eventExecutor = eventExecutor;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleLikeCreated(LikeCreatedEvent event) {
        eventExecutor.execute(() -> {
            try {
                transactionTemplate.executeWithoutResult(status ->
                    productRepository.incrementLikeCount(event.productId())
                );
            } catch (Exception e) {
                log.warn("incrementLikeCount 실패 — best-effort", e);
            }
        });
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleLikeRemoved(LikeRemovedEvent event) {
        eventExecutor.execute(() -> {
            try {
                transactionTemplate.executeWithoutResult(status ->
                    productRepository.decrementLikeCount(event.productId())
                );
            } catch (Exception e) {
                log.warn("decrementLikeCount 실패 — best-effort", e);
            }
        });
    }
}
