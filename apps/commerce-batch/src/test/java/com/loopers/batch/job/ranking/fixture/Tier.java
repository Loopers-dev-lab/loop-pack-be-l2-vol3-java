package com.loopers.batch.job.ranking.fixture;

/**
 * 시드 데이터의 상품 활동 계층.
 * 설계.md "트래픽 전제" 의 5-tier 분포에 매핑된다.
 */
public enum Tier {
    HOT,        // 대박 상품 (소수가 전체 이벤트의 큰 비중을 차지)
    WARM,       // 잘 나가는 상품
    NORMAL,     // 꾸준 판매
    COLD,       // 롱테일
    SLEEPING    // 비활동 (이벤트 0)
}
