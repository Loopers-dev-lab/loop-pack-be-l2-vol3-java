package com.loopers.application.product;

import com.loopers.domain.common.event.ProductViewedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 상품 조회 이벤트 리스너
 *
 * 상품 조회(ProductFacade.getProductDetail)는 TX가 없으므로
 * @TransactionalEventListener 대신 @EventListener를 사용한다.
 *
 * Outbox를 사용하지 않는 이유:
 *   - 조회에는 비즈니스 TX가 없음 → "같은 TX에 저장" 불가
 *   - 조회 수 유실은 서비스 정합성에 영향 없음
 *   - 추후 Kafka 직접 발행(user-activity-events-v1)으로 전환
 */
@Component
public class ProductEventListener {

    private static final Logger log = LoggerFactory.getLogger(ProductEventListener.class);

    @Async
    @EventListener
    public void handleProductViewed(ProductViewedEvent event) {
        log.info("[ProductEventListener] 상품 조회 — productId={}, userId={}",
                event.productId(), event.userId());
        // 추후: Kafka user-activity-events-v1 직접 발행
        // 또는: catalog-events-v1 직접 발행 (view_count 집계)
        // Outbox 없이 kafkaTemplate.send() — 유실 허용
    }
}
