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
    private final AdmissionStorageStrategy admissionStorageStrategy;
    private final OrderQueuePollingIntervalPolicy orderQueuePollingIntervalPolicy;

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
        final OrderQueueRealtimeStatusResult realtimeStatus = getRealtimeStatus(memberId);
        return new OrderQueueStatusResult(
                realtimeStatus.enabled(),
                realtimeStatus.displayWaitingOrder(),
                realtimeStatus.estimatedWaitSeconds()
        );
    }

    @Transactional(readOnly = true)
    public OrderQueueRealtimeStatusResult getRealtimeStatus(String memberId) {
        if (!orderQueueProperties.enabled()) {
            return OrderQueueRealtimeStatusResult.disabled();
        }

        final Long rank = orderQueueRepository.rank(memberId);
        if (rank == null && !admissionStorageStrategy.hasActiveClaim(memberId) && !admissionStorageStrategy.hasValidToken(memberId)) {
            throw new CoreException(ErrorType.NOT_FOUND, "대기열에 진입한 사용자만 조회할 수 있습니다.");
        }

        final long safeRank = rank == null ? -1L : rank;
        final String admissionState = resolveAdmissionState(memberId, rank);
        final long displayWaitingOrder = safeRank < 0 ? 0L : safeRank + 1L;
        final long throughputPerSecond = orderQueueProperties.effectiveOrderThroughputPerSecond();
        final long estimatedWaitSeconds = safeRank < 0 ? 0L : safeRank / throughputPerSecond;
        final long recommendedPollingIntervalSeconds = orderQueuePollingIntervalPolicy.resolve(
                displayWaitingOrder,
                estimatedWaitSeconds,
                orderQueueProperties
        );
        return new OrderQueueRealtimeStatusResult(
                true,
                safeRank,
                displayWaitingOrder,
                estimatedWaitSeconds,
                recommendedPollingIntervalSeconds,
                admissionState
        );
    }

    @Transactional(readOnly = true)
    public String resolveAdmissionState(String memberId, Long rank) {
        if (admissionStorageStrategy.hasValidToken(memberId)) {
            return "ADMITTED";
        }
        if (admissionStorageStrategy.hasActiveClaim(memberId)) {
            return "CLAIMED";
        }
        if (rank != null) {
            return "WAITING";
        }
        throw new CoreException(ErrorType.NOT_FOUND, "대기열에 진입한 사용자만 조회할 수 있습니다.");
    }
}
