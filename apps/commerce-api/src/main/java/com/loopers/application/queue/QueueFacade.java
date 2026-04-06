package com.loopers.application.queue;

import com.loopers.application.queue.dto.QueueEntryResponse;
import com.loopers.application.queue.dto.QueuePositionResponse;
import com.loopers.domain.queue.QueueMode;
import com.loopers.domain.queue.RedisLockRepository;
import com.loopers.domain.queue.SessionConsumeResult;
import com.loopers.domain.queue.SessionStatus;
import com.loopers.application.queue.config.QueueProperties;
import com.loopers.interfaces.scheduler.PositionCacheScheduler;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class QueueFacade {

    private static final String LOCK_ADMISSION = "lock:admission";
    private static final String LOCK_SESSION_GC = "lock:session-gc";
    private static final int LOCK_TTL_SECONDS = 15;
    private static final long DEFAULT_POSITION = 1;

    private final QueueService queueService;
    private final SessionService sessionService;
    private final ModeManager modeManager;
    private final RedisLockRepository lockRepository;
    private final PositionCacheScheduler positionCacheScheduler;
    private final QueueProperties props;
    private final MeterRegistry meterRegistry;

    // Command

    public QueueEntryResponse enter(Long userId) {
        validateEntryCondition(userId);
        cleanConsumedSession(userId);

        boolean added = queueService.addToQueue(userId);
        if (!added) {
            throw new CoreException(ErrorType.BAD_REQUEST, "대기열이 가득 찼습니다. 잠시 후 다시 시도해주세요");
        }

        guardAgainstSchedulerRace(userId);
        return buildEntryResponse(userId);
    }

    public void changeMode(QueueMode mode) {
        switch (mode) {
            case EVENT -> modeManager.switchToEvent();
            case DRAIN -> modeManager.switchToDrain();
            case NORMAL -> modeManager.switchToNormal();
        }
    }

    /**
     * 입장 스케줄러 로직. AdmissionScheduler에서 위임받아 실행.
     * 분산 락 → ZRANGE → 세션 발급(성공분만) → ZREM
     */
    public void admitBatch() {
        if (!modeManager.isEvent()) return;
        if (!lockRepository.tryLock(LOCK_ADMISSION, LOCK_TTL_SECONDS)) return;

        try {
            int batchSize = props.getAdmissionBatchSize();
            Set<String> members = queueService.peekTop(batchSize);
            if (members == null || members.isEmpty()) return;

            List<String> admitted = new ArrayList<>();
            for (String userId : members) {
                try {
                    sessionService.createSession(Long.parseLong(userId));
                    admitted.add(userId);
                } catch (Exception e) {
                    log.warn("세션 발급 실패: userId={}", userId, e);
                }
            }

            if (!admitted.isEmpty()) {
                queueService.removeFromQueue(admitted.toArray(new String[0]));
                meterRegistry.counter("admission.batch.total").increment(admitted.size());
            }
        } catch (Exception e) {
            log.error("입장 배치 처리 오류", e);
        }
    }

    /**
     * GC 스케줄러 로직. SessionGCScheduler에서 위임받아 실행.
     * 분산 락 → ZREMRANGEBYSCORE로 만료 tracker 일괄 정리
     */
    public void cleanExpiredSessions() {
        if (!modeManager.isEvent() && !modeManager.isDrain()) return;
        if (!lockRepository.tryLock(LOCK_SESSION_GC, LOCK_TTL_SECONDS)) return;

        try {
            double now = Instant.now().getEpochSecond();
            sessionService.removeExpiredTrackerEntries(now);
        } catch (Exception e) {
            log.error("SessionGC 오류", e);
        }
    }

    // 세션 CAS — OrderFacade에서 호출 (크로스 도메인은 Facade 경유)

    public SessionConsumeResult consumeSession(Long userId) {
        return sessionService.compareAndSwap(userId, SessionStatus.ACTIVE, SessionStatus.CONSUMED);
    }

    public void restoreSession(Long userId) {
        try {
            sessionService.compareAndSwap(userId, SessionStatus.CONSUMED, SessionStatus.ACTIVE);
        } catch (Exception e) {
            log.warn("세션 복원 실패: userId={}, Access TTL 만료가 복구 수단", userId, e);
        }
    }

    public void deleteSessionAfterCommit(Long userId) {
        try {
            sessionService.deleteSession(userId);
        } catch (Exception e) {
            log.warn("afterCommit 세션 삭제 실패: userId={}, Hard TTL이 정리", userId, e);
        }
    }

    // Query

    public QueuePositionResponse getPosition(Long userId) {
        // DRAIN/NORMAL: 스케줄러 중단 → 대기열 유저에게 종료 알림
        if (!modeManager.isEvent()) {
            // 이미 입장한 유저는 세션으로 블프 이용 중 → READY
            if (sessionService.hasActiveSession(userId)) {
                return QueuePositionResponse.ready();
            }
            return QueuePositionResponse.eventEnded();
        }

        if (sessionService.hasActiveSession(userId)) {
            return QueuePositionResponse.ready();
        }

        Long cachedPosition = positionCacheScheduler.getCachedPosition(userId);
        if (cachedPosition != null) {
            return QueuePositionResponse.waiting(cachedPosition, queueService.estimateWaitSeconds(cachedPosition));
        }

        Long position = queueService.getPosition(userId);
        if (position == null) {
            return QueuePositionResponse.notInQueue();
        }

        return QueuePositionResponse.waiting(position, queueService.estimateWaitSeconds(position));
    }

    private void validateEntryCondition(Long userId) {
        if (modeManager.isFallbackMode()) {
            throw new CoreException(ErrorType.SERVICE_UNAVAILABLE,
                    "일시적으로 대기열 진입이 불가합니다. 잠시 후 다시 시도해주세요");
        }
        if (!modeManager.isEvent()) {
            String message = modeManager.isDrain() ? "대기열이 마감되었습니다" : "이벤트가 진행 중이 아닙니다";
            throw new CoreException(ErrorType.BAD_REQUEST, message);
        }

        SessionService.SessionInfo session = sessionService.getSession(userId);
        if (session != null && session.status() == SessionStatus.ACTIVE) {
            throw new CoreException(ErrorType.CONFLICT, "이미 입장하셨습니다");
        }
    }

    private void cleanConsumedSession(Long userId) {
        SessionService.SessionInfo session = sessionService.getSession(userId);
        if (session != null && session.status() == SessionStatus.CONSUMED) {
            sessionService.deleteSession(userId);
        }
    }

    private void guardAgainstSchedulerRace(Long userId) {
        if (sessionService.hasSession(userId)) {
            queueService.removeFromQueue(userId.toString());
            throw new CoreException(ErrorType.CONFLICT, "이미 입장하셨습니다");
        }
    }

    private QueueEntryResponse buildEntryResponse(Long userId) {
        Long position = queueService.getPosition(userId);
        long pos = (position != null) ? position : DEFAULT_POSITION;
        long estimatedWait = queueService.estimateWaitSeconds(pos);
        return QueueEntryResponse.waiting(pos, estimatedWait);
    }
}
