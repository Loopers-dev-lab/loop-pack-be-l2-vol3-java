package com.loopers.application.product;

import com.loopers.domain.product.ProductViewLogModel;
import com.loopers.domain.product.ProductViewLogRepository;
import com.loopers.domain.product.event.ProductViewedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProductViewCountHandler {

    private final ProductViewLogRepository productViewLogRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(ProductViewedEvent event) {
        try {
            productViewLogRepository.save(
                    ProductViewLogModel.create(event.productId(), event.userId(), event.viewedAt()));
        } catch (Exception e) {
            log.warn("[ViewCount] 조회수 기록 실패 — productId={}", event.productId(), e);
        }
    }
}
