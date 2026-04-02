package com.loopers.application.queue;

import com.loopers.domain.queue.QueuePosition;
import com.loopers.domain.queue.QueueRepository;
import com.loopers.config.QueueProperties;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;

@RequiredArgsConstructor
@Component
public class QueueFacade {

    private final QueueRepository queueRepository;
    private final QueueProperties queueProperties;

    public void enter(long userId) {
        if (queueRepository.isInWaiting(userId) || queueRepository.findToken(userId).isPresent()) {
            return;
        }
        queueRepository.enter(userId, System.currentTimeMillis());
    }

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

    public void issueTokens() {
        List<String> uuids = IntStream.range(0, queueProperties.batchSize())
                .mapToObj(ignored -> UUID.randomUUID().toString())
                .toList();
        queueRepository.issueTokens(queueProperties.batchSize(), queueProperties.tokenTtlSeconds(), uuids);
    }

    public boolean validateToken(long userId, String token) {
        return queueRepository.findToken(userId)
                .filter(token::equals)
                .isPresent();
    }

    public void removeToken(long userId) {
        queueRepository.removeToken(userId);
    }
}
