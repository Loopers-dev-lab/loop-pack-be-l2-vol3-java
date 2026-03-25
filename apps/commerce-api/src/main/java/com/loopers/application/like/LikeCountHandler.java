package com.loopers.application.like;

import com.loopers.domain.product.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 좋아요 수 증가/감소 처리를 담당하는 핸들러.
 *
 * LikeEventListener에서 분리한 이유:
 * - @TransactionalEventListener(AFTER_COMMIT)은 트랜잭션 밖에서 실행됨
 * - 별도 빈의 @Transactional을 통해 새 트랜잭션을 확실하게 생성
 * (PaymentResultHandler와 동일한 패턴)
 */
@RequiredArgsConstructor
@Component
public class LikeCountHandler {

    private final ProductService productService;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void increaseLikeCount(Long productId) {
        productService.increaseLikeCount(productId);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void decreaseLikeCount(Long productId) {
        productService.decreaseLikeCount(productId);
    }
}
