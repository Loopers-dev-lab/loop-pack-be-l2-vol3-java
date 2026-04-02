package com.loopers.application.logging;

import com.loopers.domain.like.ProductLikedEvent;
import com.loopers.domain.like.ProductUnlikedEvent;
import com.loopers.domain.order.OrderRequestedEvent;
import com.loopers.domain.product.ProductViewedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class UserActionLogListener {

    @EventListener
    public void handleProductViewed(ProductViewedEvent event) {
        log.info("상품 조회. productId={}", event.getProductId());
    }

    @EventListener
    public void handleProductLiked(ProductLikedEvent event) {
        log.info("좋아요. productId={}, userId={}", event.getProductId(), event.getUserId());
    }

    @EventListener
    public void handleProductUnliked(ProductUnlikedEvent event) {
        log.info("좋아요 취소. productId={}, userId={}", event.getProductId(), event.getUserId());
    }

    @EventListener
    public void handleOrderRequested(OrderRequestedEvent event) {
        log.info("주문 요청. userId={}", event.getUserId());
    }
}
