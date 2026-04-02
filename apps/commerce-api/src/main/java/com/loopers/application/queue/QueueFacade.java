package com.loopers.application.queue;

import com.loopers.domain.queue.QueuePosition;
import com.loopers.domain.queue.QueueRepository;
import com.loopers.infrastructure.queue.QueueProperties;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@RequiredArgsConstructor
@Component
public class QueueFacade {

    private final QueueRepository queueRepository;
    private final QueueProperties queueProperties;

    @Transactional
    public void enter(long userId) {
        if (queueRepository.isInWaiting(userId) || queueRepository.findToken(userId).isPresent()) {
            return;
        }
        queueRepository.enter(userId, System.currentTimeMillis());
    }

    @Transactional(readOnly = true)
    public QueuePositionResult getPosition(long userId) {
        Optional<String> token = queueRepository.findToken(userId);
        if (token.isPresent()) {
            return new QueuePositionResult.Entered(token.get());
        }
        return queueRepository.getRank(userId)
                .map(rank -> {
                    QueuePosition position = new QueuePosition(rank);
                    return (QueuePositionResult) new QueuePositionResult.Waiting(
                            rank,
                            position.estimatedWaitSeconds(queueProperties.schedulerIntervalMs(), queueProperties.batchSize()),
                            position.nextPollAfter(queueProperties.schedulerIntervalMs(), queueProperties.batchSize())
                    );
                })
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "대기열에 진입하지 않은 사용자입니다."));
    }
}
