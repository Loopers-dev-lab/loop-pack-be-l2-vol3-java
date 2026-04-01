package com.loopers.domain.queue;

/**
 * ZSET에서 순번(ZRANK)과 총 대기 인원(ZCARD)을 한 번에 읽은 스냅샷.
 * 두 값은 Lua로 원자적으로 조회한다.
 */
public record QueuePositionSnapshot(long position, long totalWaiting) {
}
