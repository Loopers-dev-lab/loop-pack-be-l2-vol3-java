package com.loopers.application.product;

/**
 * 특정 상품 상세 캐시를 무효화하는 이벤트.
 * 재고 차감(주문), 상품 정보 수정/삭제, 브랜드명 수정 등 민감한 데이터 변경 시 발행.
 * AFTER_COMMIT에서 처리되어 커밋 전 구 버전 재캐싱 레이스를 방지한다.
 */
public record ProductDetailCacheEvictEvent(Long productId) {
}
