package com.loopers.interfaces.event.product;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import com.loopers.application.product.cache.ProductCacheWriter;
import com.loopers.domain.like.LikeEvent;
import com.loopers.domain.order.OrderEvent;
import com.loopers.domain.product.ProductService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 상품 도메인의 이벤트 리스너.
 *
 * <p>상품 도메인에 영향을 주는 이벤트를 수신하여 후속 처리를 수행한다.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProductEventListener {

    private final ProductService productService;
    private final ProductCacheWriter productCacheWriter;

    /**
     * 좋아요 생성 이벤트를 처리한다.
     *
     * <p>상품의 비정규화된 좋아요 수를 증가시키고 캐시를 갱신한다.</p>
     *
     * @param event 좋아요 생성 이벤트
     */
    @Async
    @TransactionalEventListener
    public void handle(LikeEvent.Liked event) {
        try {
            productCacheWriter.increaseLikeCount(event.productId());
        } catch (Exception e) {
            log.error("좋아요 수 증가 실패 [productId={}]", event.productId(), e);
        }
    }

    /**
     * 좋아요 취소 이벤트를 처리한다.
     *
     * <p>상품의 비정규화된 좋아요 수를 감소시키고 캐시를 갱신한다.</p>
     *
     * @param event 좋아요 취소 이벤트
     */
    @Async
    @TransactionalEventListener
    public void handle(LikeEvent.Unliked event) {
        try {
            productCacheWriter.decreaseLikeCount(event.productId());
        } catch (Exception e) {
            log.error("좋아요 수 감소 실패 [productId={}]", event.productId(), e);
        }
    }

    /**
     * 주문 실패 이벤트를 처리한다.
     *
     * <p>주문 항목별로 차감된 재고를 복원한다.</p>
     *
     * @param event 주문 실패 이벤트
     */
    @Async
    @TransactionalEventListener
    public void handle(OrderEvent.OrderFailed event) {
        event.orderItems().forEach(item -> {
            try {
                productService.restoreStock(item.productId(), item.quantity());
            } catch (Exception e) {
                log.error("재고 복원 실패 [orderId={}, productId={}, quantity={}]",
                        event.orderId(), item.productId(), item.quantity(), e);
            }
        });
    }
}
