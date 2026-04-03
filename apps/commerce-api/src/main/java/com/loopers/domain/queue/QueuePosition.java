package com.loopers.domain.queue;

public record QueuePosition(
        long position,                  // 1-based 순번
        long totalWaiting,              // 전체 대기 인원
        long estimatedWaitSeconds,      // 예상 대기 시간 (초)
        boolean tokenIssued,            // true면 클라이언트가 주문 API로 전환
        int recommendedPollingIntervalMs // 클라이언트 폴링 권장 주기
) {
    public static QueuePosition of(long rank, long totalCount, int batchSize, int intervalMs) {
        long position = rank + 1;
        // 예상 대기 시간 = (내 순번 / 배치 크기) × 스케줄러 간격
        long estimatedWaitSeconds = (position / batchSize) * intervalMs / 1000;

        // 순번에 따른 동적 폴링 주기 — 앞 순번일수록 짧은 주기로 조회
        int pollingInterval;
        if (position <= 100) {
            pollingInterval = 1000;
        } else if (position <= 1000) {
            pollingInterval = 3000;
        } else {
            pollingInterval = 5000;
        }

        return new QueuePosition(position, totalCount, estimatedWaitSeconds, false, pollingInterval);
    }

    public static QueuePosition ofTokenIssued() {
        return new QueuePosition(0, 0, 0, true, 0);
    }

    public static QueuePosition ofExpired() {
        return new QueuePosition(0, 0, 0, false, 0);
    }
}
