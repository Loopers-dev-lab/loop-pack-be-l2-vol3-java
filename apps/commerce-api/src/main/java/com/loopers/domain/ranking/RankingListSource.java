package com.loopers.domain.ranking;

/**
 * 랭킹 목록이 어떤 저장소·경로에서 채워졌는지 구분한다.
 * <p>
 * Redis 장애 시 빈 목록과 실제 인기 없음을 응답만으로 구분하기 어려우므로, API에는 {@code dataSource}로 내려준다.
 */
public enum RankingListSource {

    /** 일간 Redis ZSET 기준 인기 랭킹 */
    REDIS_ZSET,

    /** {@link RankingKey#snapshot} ZSET 복제본 기준(페이지 넘김 동안 순서 고정) */
    REDIS_ZSET_SNAPSHOT,

    /** Redis 실패 시 DB 최신 등록순 대체 목록(§4.2 fallback) */
    FALLBACK_DB_LATEST,

    /** Redis를 읽지 못했고 fallback도 쓰지 않았거나 결과가 비었을 때 */
    DEGRADED_EMPTY,

    /** 주간 랭킹 MV({@code mv_product_rank_weekly}) */
    MV_WEEKLY,

    /** 월간 랭킹 MV({@code mv_product_rank_monthly}) */
    MV_MONTHLY
}
