package com.loopers.application.queue;

import com.loopers.domain.queue.EntryToken;
import com.loopers.domain.queue.EntryTokenRepository;
import com.loopers.domain.queue.WaitingQueue;
import com.loopers.domain.queue.WaitingQueueRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorMessage;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class QueueFacade {

    // 임의 TPS
    // TODO 부하 테스트후 다시 설정 필요
    static final int TPS = 200;

    private final WaitingQueueRepository waitingQueueRepository;
    private final EntryTokenRepository entryTokenRepository;

    public QueueInfo enter(Long userId) {
        try {
            long position = waitingQueueRepository.enter(userId);
            long totalWaiting = waitingQueueRepository.getSize();
            WaitingQueue queue = WaitingQueue.of(userId, position, totalWaiting);
            return QueueInfo.from(queue, TPS);
        } catch (RedisConnectionFailureException | QueryTimeoutException e) {
            log.error("Redis unavailable on queue enter. userId={}", userId, e);
            throw new CoreException(ErrorType.SERVICE_UNAVAILABLE, ErrorMessage.Queue.QUEUE_UNAVAILABLE);
        }
    }

    public QueueInfo getPosition(Long userId) {
        try {
            long position = waitingQueueRepository.getPosition(userId);
            if (position == -1L) {
                return entryTokenRepository.findByUserId(userId)
                    .map(token -> QueueInfo.admitted(token.getToken()))
                    .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, ErrorMessage.Queue.NOT_IN_QUEUE));
            }
            long totalWaiting = waitingQueueRepository.getSize();
            WaitingQueue queue = WaitingQueue.of(userId, position, totalWaiting);
            return QueueInfo.from(queue, TPS);
        } catch (RedisConnectionFailureException | QueryTimeoutException e) {
            log.error("Redis unavailable on getPosition. userId={}", userId, e);
            throw new CoreException(ErrorType.SERVICE_UNAVAILABLE, ErrorMessage.Queue.QUEUE_UNAVAILABLE);
        }
    }

    public void validateAndConsumeToken(Long userId, String token) {
        try {
            EntryToken entryToken = entryTokenRepository.findByUserId(userId)
                .orElseThrow(() -> new CoreException(ErrorType.FORBIDDEN, ErrorMessage.Queue.INVALID_ENTRY_TOKEN));
            if (!entryToken.getToken().equals(token)) {
                throw new CoreException(ErrorType.FORBIDDEN, ErrorMessage.Queue.INVALID_ENTRY_TOKEN);
            }
            entryTokenRepository.deleteByUserId(userId);
        } catch (CoreException e) {
            throw e;
        } catch (RedisConnectionFailureException | QueryTimeoutException e) {
            log.error("Redis unavailable on validateAndConsumeToken. userId={}", userId, e);
            throw new CoreException(ErrorType.SERVICE_UNAVAILABLE, ErrorMessage.Queue.QUEUE_UNAVAILABLE);
        }
    }
}
