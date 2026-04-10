package com.loopers.application.collector;

import java.time.Instant;

/**
 * PRODUCT_VIEWED 이벤트의 랭킹 기여 허용 여부를 판단한다.
 */
public interface ProductViewContributionLimiter {

    /**
     * @param partitionKey 유저/세션/디바이스를 대표하는 키
     * @param productId 상품 ID
     * @param occurredAt 이벤트 발생 시각
     * @return true면 view_count 기여를 허용, false면 상한 초과로 차단
     */
    boolean allowContribution(String partitionKey, long productId, Instant occurredAt);
}
