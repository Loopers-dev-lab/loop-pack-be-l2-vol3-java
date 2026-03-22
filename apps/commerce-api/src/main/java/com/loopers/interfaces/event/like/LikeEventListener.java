package com.loopers.interfaces.event.like;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import com.loopers.domain.like.LikeService;
import com.loopers.domain.product.ProductEvent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 좋아요 도메인의 이벤트 리스너.
 *
 * <p>좋아요 도메인에 영향을 주는 이벤트를 수신하여 후속 처리를 수행한다.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LikeEventListener {

    private final LikeService likeService;

    /**
     * 상품 삭제 이벤트를 처리한다.
     *
     * <p>해당 상품의 좋아요를 일괄 삭제한다.</p>
     *
     * @param event 상품 삭제 이벤트
     */
    @Async
    @TransactionalEventListener
    public void handle(ProductEvent.ProductDeleted event) {
        try {
            likeService.deleteLikesByProductId(event.productId());
        } catch (Exception e) {
            log.error("상품 삭제 이벤트 처리 실패: 좋아요 삭제 [productId={}]", event.productId(), e);
        }
    }
}
