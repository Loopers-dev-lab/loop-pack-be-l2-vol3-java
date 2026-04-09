package com.loopers.domain.ranking;

import java.util.List;

/**
 * 상품 랭킹 데이터를 조회하는 포트.
 *
 * <p>Redis Sorted Set에 적재된 랭킹 데이터를 읽기 전용으로 제공한다.</p>
 */
public interface RankingRepository {

    /**
     * 상위 랭킹 항목을 순위 내림차순으로 조회한다.
     *
     * @param key    Redis 키
     * @param offset 시작 오프셋 (0-based)
     * @param count  조회할 항목 수
     * @return 순위가 포함된 랭킹 항목 목록
     */
    List<RankingItem> readTopRanked(String key, int offset, int count);

    /**
     * 특정 상품의 순위를 조회한다.
     *
     * @param key       Redis 키
     * @param productId 상품 ID
     * @return 1-based 순위, 순위권 밖이면 null
     */
    Integer findRank(String key, Long productId);
}
