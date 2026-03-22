package com.loopers.interfaces.event.like;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import com.loopers.application.product.cache.ProductCacheWriter;
import com.loopers.domain.like.LikeEvent;

import lombok.RequiredArgsConstructor;

/**
 * 좋아요 도메인 이벤트를 수신하여 상품의 좋아요 수를 갱신하는 리스너.
 *
 * <p>좋아요 트랜잭션 커밋 후 비동기로 실행되며, 상품의 비정규화된 좋아요 수와 캐시를 갱신한다.</p>
 */
@Component
@RequiredArgsConstructor
public class LikeEventListener {

    private final ProductCacheWriter productCacheWriter;

    /**
     * 좋아요 생성 이벤트를 처리한다.
     *
     * @param event 좋아요 생성 이벤트
     */
    @Async
    @TransactionalEventListener
    public void handle(LikeEvent.Liked event) {
        productCacheWriter.increaseLikeCount(event.productId());
    }

    /**
     * 좋아요 취소 이벤트를 처리한다.
     *
     * @param event 좋아요 취소 이벤트
     */
    @Async
    @TransactionalEventListener
    public void handle(LikeEvent.Unliked event) {
        productCacheWriter.decreaseLikeCount(event.productId());
    }
}
