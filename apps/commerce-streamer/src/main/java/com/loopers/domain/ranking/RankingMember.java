package com.loopers.domain.ranking;

/**
 * Redis ZSET 랭킹에서 사용하는 member 규칙을 관리한다.
 * <p>
 * member는 상품 식별자(productId)를 문자열로 변환한 값을 사용한다.
 */
public final class RankingMember {

    /**
     * 유틸리티 클래스의 인스턴스화를 방지한다.
     */
    private RankingMember() {
    }

    /**
     * 상품 ID를 ZSET member 문자열로 변환한다.
     *
     * @param productId 상품 ID
     * @return Redis ZSET member 문자열
     * @throws IllegalArgumentException productId가 0 이하인 경우
     */
    public static String fromProductId(long productId) {
        if (productId <= 0L) {
            throw new IllegalArgumentException("productId must be positive");
        }
        return String.valueOf(productId);
    }
}
