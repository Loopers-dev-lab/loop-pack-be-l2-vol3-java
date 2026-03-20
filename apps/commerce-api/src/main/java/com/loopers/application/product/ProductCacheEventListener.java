package com.loopers.application.product;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@RequiredArgsConstructor
@Component
public class ProductCacheEventListener {

    private final ProductCacheRepository productCacheRepository;

    /**
     * DB 트랜잭션 커밋 완료 후에 캐시 무효화 실행.
     * BEFORE_COMMIT이 아닌 AFTER_COMMIT을 사용하는 이유:
     * 커밋 전에 캐시를 삭제하면, 삭제~커밋 사이 구간에 다른 요청이
     * 캐시 미스 → DB 조회(구 버전) → 구 버전 재캐싱하는 레이스가 발생하기 때문.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleProductChanged(ProductCacheEvictEvent event) {
        productCacheRepository.evictAll();
    }

    // 특정 상품의 상세 캐시만 핀포인트 무효화 (주문 재고 차감, 상품/브랜드 수정 시 호출)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleProductDetailChanged(ProductDetailCacheEvictEvent event) {
        productCacheRepository.evictDetail(event.productId());
    }
}
