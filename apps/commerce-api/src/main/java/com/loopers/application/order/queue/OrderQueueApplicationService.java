package com.loopers.application.order.queue;

import com.loopers.domain.orderqueue.OrderQueueRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OrderQueueApplicationService {

    private final OrderQueueRepository orderQueueRepository;
    private final OrderQueueProperties orderQueueProperties;

    @Transactional
    public OrderQueueStatusResult enter(String memberId) {
        if (!orderQueueProperties.enabled()) {
            return OrderQueueStatusResult.disabled();
        }

        final long enteredAt = System.currentTimeMillis();
        orderQueueRepository.upsert(memberId, enteredAt);
        return getStatus(memberId);
    }

    @Transactional(readOnly = true)
    public OrderQueueStatusResult getStatus(String memberId) {
        if (!orderQueueProperties.enabled()) {
            return OrderQueueStatusResult.disabled();
        }

        final Long rank = orderQueueRepository.rank(memberId);
        if (rank == null) {
            throw new CoreException(ErrorType.NOT_FOUND, "대기열에 진입한 사용자만 조회할 수 있습니다.");
        }

        final long waitingOrder = rank + 1L;
        final long throughputPerSecond = Math.max(1L, orderQueueProperties.throughputPerSecond());
        final long estimatedWaitSeconds = rank / throughputPerSecond;
        return new OrderQueueStatusResult(true, waitingOrder, estimatedWaitSeconds);
    }
}
