package com.loopers.domain.ranking;

import java.util.function.ObjDoubleConsumer;

/**
 * 랭킹 ZSET 읽기 인터페이스 (DIP) - Carry-Over 스케줄러 전용.
 *
 * commerce-api 쪽의 읽기 경로(ZREVRANGE/ZREVRANK) 와는 별도로,
 * streamer 내부에서만 사용되는 "오늘 키 전체 순회" 용도.
 */
public interface RankingReader {

    /**
     * 지정 key 의 모든 (productId, score) 쌍을 순회하며 consumer 에 전달한다.
     * 키가 없으면 no-op.
     */
    void forEachWithScore(String key, ObjDoubleConsumer<Long> consumer);
}
