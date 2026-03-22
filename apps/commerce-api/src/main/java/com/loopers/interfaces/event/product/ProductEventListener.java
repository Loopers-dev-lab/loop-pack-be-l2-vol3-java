package com.loopers.interfaces.event.product;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import com.loopers.domain.like.LikeService;
import com.loopers.domain.product.ProductEvent.ProductDeleted;

import lombok.RequiredArgsConstructor;

/**
 * 상품 도메인 이벤트를 수신하여 연관 데이터를 정리하는 리스너.
 *
 * <p>상품 삭제 트랜잭션 커밋 후 비동기로 실행되며,
 * 해당 상품의 좋아요를 일괄 삭제한다.</p>
 */
@Component
@RequiredArgsConstructor
public class ProductEventListener {

    private final LikeService likeService;

    /**
     * 상품 삭제 이벤트를 처리한다.
     *
     * @param event 상품 삭제 이벤트
     */
    @Async
    @TransactionalEventListener
    public void handle(ProductDeleted event) {
        likeService.deleteLikesByProductId(event.productId());
    }
}
