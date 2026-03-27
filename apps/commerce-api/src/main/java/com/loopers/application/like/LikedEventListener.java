package com.loopers.application.like;

import com.loopers.domain.like.LikedEvent;
import com.loopers.domain.product.ProductRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@RequiredArgsConstructor
@Component
public class LikedEventListener {

    private final ProductRepository productRepository;

    // AFTER_COMMIT: Like 저장 트랜잭션이 커밋된 이후에 실행된다.
    // 원래 트랜잭션은 이미 끝났기 때문에, REQUIRES_NEW로 새 트랜잭션을 열어야 한다.
    // 이 구조는 나중에 Kafka로 이관할 때와 동일한 개념이다.
    // (Kafka Consumer도 별도 트랜잭션에서 처리하기 때문)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Caching(evict = {
        @CacheEvict(value = "product:detail", key = "#event.productId()"),
        @CacheEvict(value = "product:list", allEntries = true)
    })
    public void handle(LikedEvent event) {
        var product = productRepository.findById(event.productId())
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[id = " + event.productId() + "] 상품을 찾을 수 없습니다."));
        product.increaseLikeCount();
    }
}
