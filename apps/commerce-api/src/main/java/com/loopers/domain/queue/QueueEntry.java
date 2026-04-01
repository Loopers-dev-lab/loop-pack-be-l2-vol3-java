package com.loopers.domain.queue;

/**
 * 대기열에서 꺼낸 항목. ZPOPMIN 결과를 도메인 모델로 변환.
 *
 * @param userId 사용자 ID
 * @param score  진입 시각 (밀리초 타임스탬프)
 */
public record QueueEntry(Long userId, double score) {
}
