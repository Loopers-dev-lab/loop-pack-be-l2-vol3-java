package com.loopers.application.collector;

import java.time.Instant;

/**
 * 유저/주문 식별자(partitionKey) + 상품 + 일자 단위 판매(수량) 기여 상한.
 */
public interface ProductSoldContributionLimiter {

    /**
     * 상한 내에서 수량을 소비할 수 있으면 true. Redis 등에서 원자적으로 누적한다.
     *
     * @param partitionKey 프로듀서가 넣는 식별자(예: 구매자/세션). 비어 있으면 상한 미적용.
     * @param productId    상품 ID
     * @param quantity     이번 라인 수량(양수)
     * @param occurredAt   이벤트 시각(일자 산정용, KST)
     */
    boolean allowContribution(String partitionKey, long productId, long quantity, Instant occurredAt);
}
