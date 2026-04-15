package com.loopers.domain.ranking;

import java.time.Duration;

/**
 * 일간 ZSET의 읽기 전용 복제본(스냅샷)을 Redis에 올려, 오프셋 페이징 시 목록 순서가 바뀌지 않게 한다.
 */
public interface RankingSnapshotRepository {

    /**
     * {@code sourceKey} ZSET을 {@code snapshotKey}에 복사하고 TTL을 설정한다.
     * <p>
     * Redis {@code ZUNIONSTORE snapshot 1 source}와 동등하며, 원본 키가 없으면 빈 ZSET이 된다.
     *
     * @param sourceKey   일간 랭킹 키
     * @param snapshotKey 스냅샷 키
     * @param ttl         스냅샷 보존 기간
     * @return 복사 후 ZSET 원소 수
     */
    long materialize(String sourceKey, String snapshotKey, Duration ttl);

    /**
     * 스냅샷 키가 존재하는지 여부
     *
     * @param snapshotKey 스냅샷 키
     * @return 존재하면 true
     */
    boolean exists(String snapshotKey);
}
