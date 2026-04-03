package com.loopers.interfaces.api.queue;

import com.loopers.application.queue.QueueInfo;

public class QueueV1Dto {

    /**
     * POST /api/v1/queue/enter 요청
     */
    public record EnterRequest(Long userId, String queueId) {}

    /**
     * 진입 응답.
     * token: 폴링 시 헤더에 담아 전달
     * rank: 현재 순위 (1-based로 변환해서 반환 — 유저 친화적)
     * totalSize: 전체 대기 인원
     */
    public record EnterResponse(String token, long rank, long totalSize) {
        public static EnterResponse from(QueueInfo.EnterInfo info) {
            return new EnterResponse(info.token(), info.rank() + 1, info.totalSize());
        }
    }

    /**
     * 폴링 응답.
     * rank: 현재 순위 (1-based)
     * totalSize: 전체 대기 인원
     * etaSeconds: 예상 대기 시간 (초)
     * admitted: true이면 서비스 진입 가능
     *
     * 클라이언트 적응형 폴링 인터벌 가이드:
     * rank 1~100   → 1초
     * rank 101~1000 → 3초
     * rank 1000+   → 5초
     * (+ Jitter: 각 인터벌의 0~50% 랜덤 추가 → Thundering Herd 방지)
     */
    public record StatusResponse(long rank, long totalSize, long etaSeconds, boolean admitted) {
        public static StatusResponse from(QueueInfo.StatusInfo info) {
            return new StatusResponse(info.rank() + 1, info.totalSize(), info.etaSeconds(), info.admitted());
        }
    }
}
