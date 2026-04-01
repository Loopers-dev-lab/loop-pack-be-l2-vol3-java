package com.loopers.application.queue;

import com.loopers.domain.queue.EntryTokenService;
import com.loopers.domain.queue.QueueProperties;
import com.loopers.domain.queue.QueueService;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class QueueFacade {

    private final QueueService queueService;
    private final EntryTokenService entryTokenService;
    private final QueueProperties queueProperties;

    public QueueInfo enter(Long userId) {
        Long rank = queueService.enter(userId);
        Long totalWaiting = queueService.getQueueSize();
        return QueueInfo.of(rank, totalWaiting);
    }

    /**
     * 순번 조회 + 토큰 확인 + 예상 대기 시간 계산
     *
     * 유저 상태는 3가지:
     * 1. rank != null          → 아직 대기 중 (대기열에 있음)
     * 2. rank == null, 토큰 있음 → 입장 가능! (토큰 발급됨)
     * 3. rank == null, 토큰 없음 → 진입하지 않았거나 토큰 만료
     */
    public QueuePositionInfo getPosition(Long userId) {
        Long rank = queueService.getRank(userId);
        Long totalWaiting = queueService.getQueueSize();
        String token = entryTokenService.getToken(userId);

        if (rank != null) {
            // 아직 대기 중
            long estimatedWaitSeconds = (long) Math.ceil((double) rank / queueProperties.throughputPerSecond());
            return QueuePositionInfo.waiting(rank, totalWaiting, estimatedWaitSeconds);
        } else if (token != null) {
            // 토큰 발급됨 = 입장 가능
            return QueuePositionInfo.ready(totalWaiting, token);
        } else {
            // 대기열에도 없고 토큰도 없음
            throw new CoreException(ErrorType.NOT_FOUND, "대기열에 존재하지 않습니다.");
        }
    }
}
