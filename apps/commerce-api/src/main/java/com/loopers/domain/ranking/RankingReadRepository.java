package com.loopers.domain.ranking;

import java.util.List;

/**
 * 일간 랭킹 ZSET 읽기 포트. 구현체는 infrastructure.
 * <p>
 * member는 상품 ID 문자열만 가정하고, 페이로드(이름·가격 등)는 ZSET에 저장하지 않는다.
 *
 * @see RankingQueryService
 */
public interface RankingReadRepository {

    long count(String key);

    /** 점수 내림차순, 인덱스 [start, end] (0부터, 양 끝 포함). */
    List<RankingZsetEntry> findReverseRangeWithScores(String key, long start, long end);
}
