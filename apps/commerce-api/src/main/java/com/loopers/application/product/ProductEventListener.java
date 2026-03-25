package com.loopers.application.product;

import com.loopers.domain.common.event.ProductViewedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 상품 이벤트 리스너
 *
 * 상품 조회는 TX가 없으므로 @TransactionalEventListener 대신 @EventListener 사용.
 * readOnly 조회에서 발행되므로 AFTER_COMMIT이 의미 없음.
 */
@Component
public class ProductEventListener {

    private static final Logger log = LoggerFactory.getLogger(ProductEventListener.class);

    /**
     * 상품 조회 → product_metrics 집계 (조회 수)
     */
    @Async
    @EventListener
    public void handleProductViewed(ProductViewedEvent event) {
        log.info("[ProductEventListener] product_metrics 집계 예정 — productId={}, userId={}",
                event.productId(), event.userId());
        // Step 2: Kafka catalog-events-v1 토픽으로 전환
        // Consumer가 product_metrics.view_count를 upsert
    }

    /**
     * 상품 조회 → 유저 행동 로깅
     */
    @Async
    @EventListener
    public void handleViewActivity(ProductViewedEvent event) {
        log.info("[ProductEventListener] 유저 행동 로깅 — userId={}, productId={}, type=VIEW",
                event.userId(), event.productId());
        // Step 2: Kafka user-activity-events-v1 토픽으로 전환
    }
}
