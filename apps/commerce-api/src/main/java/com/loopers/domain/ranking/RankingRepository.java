package com.loopers.domain.ranking;

import java.util.List;

/**
 * 랭킹 ZSET 읽기 전용 저장소 인터페이스 (DIP).
 *
 * commerce-streamer 가 상시 ZADD 를 채우므로, commerce-api 는
 * 재계산 트리거 없이 단순 `ZREVRANGE` / `ZREVRANK` 만 수행한다.
 */
public interface RankingRepository {

    /**
     * 지정 key 의 Top-N 을 반환한다. rank 는 1-based.
     *
     * @param key           ZSET 키 (`ranking:all:yyyyMMdd`)
     * @param pageOneBased  사용자 노출 기준 페이지 번호 (1-based)
     * @param size          페이지 크기
     * @return              상위 `(page-1)*size` 번째부터 `size` 개의 엔트리. 키가 비면 빈 리스트.
     */
    List<RankingEntry> getTopN(String key, int pageOneBased, int size);

    /**
     * 특정 상품의 순위를 반환한다. 없으면 null.
     * 1-based 순위로 반환한다 ({@code ZREVRANK} 는 0-based 이므로 +1).
     */
    Long getRank(String key, Long productId);

    /**
     * 지정 key 의 전체 엔트리 수.
     */
    long getTotal(String key);
}
