package com.loopers.domain.ranking;

/**
 * 스냅샷 생성 결과(클라이언트가 이후 GET에 {@code rankingSnapshotId}로 재사용).
 *
 * @param snapshotId    서버가 발급한 UUID 문자열
 * @param totalElements 복사 직후 ZSET 원소 수(ZCARD)
 * @param ttlSeconds    스냅샷 TTL(초)
 */
public record RankingSnapshotCreateResult(String snapshotId, long totalElements, long ttlSeconds) {
}
