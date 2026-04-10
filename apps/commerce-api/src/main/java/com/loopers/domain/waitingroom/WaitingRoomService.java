package com.loopers.domain.waitingroom;

import com.loopers.domain.waitingroom.event.WaitingRoomAdmittedEvent;
import com.loopers.domain.waitingroom.event.WaitingRoomCancelledEvent;
import com.loopers.domain.waitingroom.event.WaitingRoomCompletedEvent;
import com.loopers.domain.waitingroom.event.WaitingRoomEnteredEvent;
import com.loopers.support.error.CoreException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class WaitingRoomService {

    private static final long THROUGHPUT_PER_SECOND = 20;
    private static final int BATCH_SIZE = 2; // 100ms마다 2명 × 10회 = 초당 20명 (Jitter)

    private final WaitingQueue waitingQueue;
    private final EntryGate entryGate;
    private final ApplicationEventPublisher eventPublisher;

    // === (1) 줄 서기 === //

    public WaitingRoomPosition enter(Long userId) {
        waitingQueue.enter(userId);
        eventPublisher.publishEvent(new WaitingRoomEnteredEvent(userId));
        return buildPosition(userId);
    }

    // === (2) 내 순위 요청 === //

    public WaitingRoomPosition getPosition(Long userId) {
        String token = entryGate.getToken(userId);
        if (token != null) {
            return WaitingRoomPosition.ready(token);
        }
        Long rank = waitingQueue.getRank(userId);
        if (rank == null) {
            throw new CoreException(WaitingRoomErrorCode.NOT_IN_QUEUE);
        }
        long totalWaiting = waitingQueue.getTotalWaiting();
        return WaitingRoomPosition.of(rank, totalWaiting, THROUGHPUT_PER_SECOND);
    }

    // === (3) N명 꺼내기 + (4) 토큰 발급 === //

    public List<WaitingEntry> processQueue() {
        List<WaitingEntry> admitted = waitingQueue.popFrontWithScores(BATCH_SIZE);
        for (WaitingEntry entry : admitted) {
            entryGate.issueToken(entry.userId());
        }
        if (!admitted.isEmpty()) {
            eventPublisher.publishEvent(new WaitingRoomAdmittedEvent(admitted.size()));
        }
        return admitted;
    }

    // === (5a) 토큰 검증 === //

    public void validateToken(Long userId, String token) {
        entryGate.validateToken(userId, token);
    }

    // === 토큰 삭제 (주문 완료 후) === //

    public void completeEntry(Long userId) {
        entryGate.completeEntry(userId);
        eventPublisher.publishEvent(new WaitingRoomCompletedEvent(userId));
    }

    // === 대기열 취소 === //

    public void cancel(Long userId) {
        waitingQueue.cancel(userId);
        eventPublisher.publishEvent(new WaitingRoomCancelledEvent(userId));
    }

    private WaitingRoomPosition buildPosition(Long userId) {
        Long rank = waitingQueue.getRank(userId);
        long totalWaiting = waitingQueue.getTotalWaiting();
        return WaitingRoomPosition.of(rank, totalWaiting, THROUGHPUT_PER_SECOND);
    }
}
