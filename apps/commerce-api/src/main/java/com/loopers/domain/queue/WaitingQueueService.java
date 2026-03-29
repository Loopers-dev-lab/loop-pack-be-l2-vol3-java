package com.loopers.domain.queue;

import com.loopers.config.QueueProperties;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
public class WaitingQueueService {

    private final WaitingQueueRepository waitingQueueRepository;
    private final QueueProperties queueProperties;

    public boolean enter(Long memberId) {
        long currentSize = waitingQueueRepository.getTotalCount();
        if (currentSize >= queueProperties.maxSize()) {
            throw new CoreException(ErrorType.QUEUE_FULL);
        }

        double score = System.currentTimeMillis() * 1000.0 + ThreadLocalRandom.current().nextInt(1000);
        return waitingQueueRepository.enter(memberId, score);
    }

    public Optional<Long> getPosition(Long memberId) {
        return waitingQueueRepository.getPosition(memberId);
    }

    public long getTotalCount() {
        return waitingQueueRepository.getTotalCount();
    }

    public List<Long> popN(int count) {
        return waitingQueueRepository.popN(count);
    }

    public List<Map.Entry<Long, Double>> popNWithScore(int count) {
        return waitingQueueRepository.popNWithScore(count);
    }

    public void clear() {
        waitingQueueRepository.clear();
    }
}
