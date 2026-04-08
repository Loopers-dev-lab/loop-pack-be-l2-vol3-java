package com.loopers.domain.ranking;

import java.util.List;
import java.util.OptionalLong;

/**
 * 일간 랭킹 ZSET 읽기 포트. 구현체는 infrastructure.
 * <p>
 * member는 상품 ID 문자열만 가정하고, 페이로드(이름·가격 등)는 ZSET에 저장하지 않는다.
 *
 * @see RankingQueryService
 */
public interface RankingReadRepository {

    long count(String key);

    /**
     * 점수 내림차순, 인덱스 [start, end] (0부터, 양 끝 포함).
     * 동일 score 구간의 상대 순서는 Redis {@code ZREVRANGE} 규칙(동점 시 member 내림차순)을 따른다.
     */
    List<RankingZsetEntry> findReverseRangeWithScores(String key, long start, long end);

    /**
     * 점수 내림차순 기준 전역 순위(1-based). 최고점이 1위.
     * ZSET에 member가 없으면 empty를 반환한다.
     */
    OptionalLong findOneBasedReverseRank(String key, String member);
}
