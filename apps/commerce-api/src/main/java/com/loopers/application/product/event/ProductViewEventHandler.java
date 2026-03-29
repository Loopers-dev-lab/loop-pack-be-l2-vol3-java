package com.loopers.application.product.event;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ProductViewEventHandler {

    @Async
    @EventListener
    public void handle(ProductViewedEvent event) {
        try {
            log.info("상품 조회 이벤트 - productId: {}, userId: {}, viewedAt: {}",
                event.productId(), event.userId(), event.viewedAt());
        } catch (Exception e) {
            log.warn("상품 조회 이벤트 처리 실패 - productId: {}", event.productId(), e);
        }
    }
}
