package com.loopers.application.product.event;

import com.loopers.application.like.event.ProductLikedEvent;
import com.loopers.application.like.event.ProductUnlikedEvent;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
import com.loopers.infrastructure.product.ProductCacheStore;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
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
public class ProductLikeCountListener {

    private final ProductRepository productRepository;
    private final ProductCacheStore productCacheStore;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleProductLiked(ProductLikedEvent event) {
        try {
            Product product = productRepository.findById(event.productId())
                .orElseThrow(() -> new CoreException(ErrorType.PRODUCT_NOT_FOUND));
            product.increaseLikeCount();
            productRepository.save(product);
            productCacheStore.evictProduct(event.productId());
        } catch (Exception e) {
            log.warn("좋아요 집계 실패 - productId: {}", event.productId(), e);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleProductUnliked(ProductUnlikedEvent event) {
        try {
            Product product = productRepository.findById(event.productId())
                .orElseThrow(() -> new CoreException(ErrorType.PRODUCT_NOT_FOUND));
            product.decreaseLikeCount();
            productRepository.save(product);
            productCacheStore.evictProduct(event.productId());
        } catch (Exception e) {
            log.warn("좋아요 집계 취소 실패 - productId: {}", event.productId(), e);
        }
    }
}
