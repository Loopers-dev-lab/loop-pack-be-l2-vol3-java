package com.loopers.domain.queue;

// ZPOPMIN으로 대기열에서 꺼낸 유저 정보를 담는 불변 DTO.
// 토큰 발급 실패 시 원래 score(진입 시각)를 보존하여 재삽입할 수 있도록
// userId와 score를 함께 전달한다.
public record QueueEntry(Long userId, double score) {
}
