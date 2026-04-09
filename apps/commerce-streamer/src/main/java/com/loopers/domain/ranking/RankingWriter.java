package com.loopers.domain.ranking;

/**
 * 랭킹 ZSET 쓰기 인터페이스 (DIP).
 *
 * 단일 연산: {@code ZADD key score member} + 최초 1회 {@code EXPIRE}
 *
 * Redis `ZADD` 는 원자적이며 멤버 존재 여부와 무관하게 score 만 덮어쓴다.
 * 부분 실패 시 해당 상품 1건만 누락되고 다음 배치 poll 에서 자연 재반영된다.
 */
public interface RankingWriter {

    /**
     * 지정 key 의 ZSET 에 (productId, score) 를 upsert 한다.
     * 최초 키 생성 시에는 retention TTL 을 함께 설정한다.
     */
    void upsertScore(String key, Long productId, double score);
}
