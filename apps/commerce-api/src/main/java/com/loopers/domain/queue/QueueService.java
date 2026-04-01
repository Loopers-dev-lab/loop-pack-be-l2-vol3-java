package com.loopers.domain.queue;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class QueueService {

    private final QueueRepository queueRepository;

    public long enter(String userId) {
        return queueRepository.enter(userId, System.currentTimeMillis());
    }

    public long getPosition(String userId) {
        return queueRepository.findPosition(userId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "대기열에 존재하지 않는 사용자입니다."));
    }

    public long getTotalCount() {
        return queueRepository.getTotalCount();
    }
}