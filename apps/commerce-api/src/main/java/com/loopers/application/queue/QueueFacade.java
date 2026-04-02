package com.loopers.application.queue;

import com.loopers.domain.queue.QueueEntryResult;
import com.loopers.domain.queue.QueuePositionResult;
import com.loopers.domain.queue.QueueService;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class QueueFacade {

    private final QueueService queueService;

    public QueueInfo enterQueue(Long userId) {
        QueueEntryResult result = queueService.enterQueue(userId);
        return QueueInfo.from(result);
    }

    public QueuePositionInfo getPosition(Long userId) {
        QueuePositionResult result = queueService.getPosition(userId);
        if (result == null) {
            throw new CoreException(ErrorType.NOT_FOUND, "대기열에 등록되지 않은 사용자입니다.");
        }
        return QueuePositionInfo.from(result);
    }
}
