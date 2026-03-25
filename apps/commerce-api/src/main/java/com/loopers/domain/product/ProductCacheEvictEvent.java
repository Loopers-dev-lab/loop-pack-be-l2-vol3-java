package com.loopers.domain.product;

/**
 * 상품 데이터 변경 시 캐시 무효화를 요청하는 이벤트.
 * 트랜잭션 커밋 후(AFTER_COMMIT)에 처리되어
 * "미커밋 상태에서 구 버전 재캐싱" 레이스 컨디션을 방지한다.
 */
public record ProductCacheEvictEvent() {
}
