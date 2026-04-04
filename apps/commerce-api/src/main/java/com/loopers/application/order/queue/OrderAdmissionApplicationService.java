package com.loopers.application.order.queue;

import com.loopers.domain.orderqueue.OrderQueueRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class OrderAdmissionApplicationService {

    private final OrderQueueProperties orderQueueProperties;
    private final OrderQueueRepository orderQueueRepository;
    private final AdmissionStorageStrategy admissionStorageStrategy;

    @Transactional(readOnly = true)
    public void validateOrderEntry(String memberId) {
        if (!orderQueueProperties.enabled()) {
            return;
        }

        if (!admissionStorageStrategy.hasValidToken(memberId)) {
            throw new CoreException(ErrorType.FORBIDDEN, "입장 토큰이 있는 사용자만 주문할 수 있습니다.");
        }
    }

    @Transactional
    public int issueAdmissions() {
        if (!orderQueueProperties.enabled() || !orderQueueProperties.schedulerEnabled()) {
            return 0;
        }

        final long nowMillis = System.currentTimeMillis();
        final int batchSize = resolveBatchSize(nowMillis);
        if (batchSize <= 0) {
            return 0;
        }

        final List<String> candidates = orderQueueRepository.peek(batchSize);
        int issuedCount = 0;
        for (String memberId : candidates) {
            if (admissionStorageStrategy.hasValidToken(memberId)) {
                orderQueueRepository.remove(memberId);
                admissionStorageStrategy.clearClaim(memberId);
                continue;
            }

            if (!admissionStorageStrategy.tryClaim(memberId, nowMillis, orderQueueProperties.claimTtlMs())) {
                continue;
            }

            try {
                if (admissionStorageStrategy.countActiveTokens(nowMillis) >= orderQueueProperties.maxActiveAdmissions()) {
                    break;
                }

                if (admissionStorageStrategy.issueToken(memberId, nowMillis, orderQueueProperties.tokenTtlMs())) {
                    orderQueueRepository.remove(memberId);
                    issuedCount++;
                }
            } finally {
                admissionStorageStrategy.clearClaim(memberId);
            }
        }
        return issuedCount;
    }

    @Transactional
    public void completeAdmission(String memberId) {
        if (!orderQueueProperties.enabled()) {
            return;
        }

        admissionStorageStrategy.removeToken(memberId);
        orderQueueRepository.remove(memberId);
        admissionStorageStrategy.clearClaim(memberId);
    }

    @Transactional(readOnly = true)
    public int resolveBatchSize(long nowMillis) {
        if (!orderQueueProperties.dynamicAdjustmentEnabled()) {
            return orderQueueProperties.initialBatchSize();
        }

        final long activeTokens = admissionStorageStrategy.countActiveTokens(nowMillis);
        final long remainingCapacity = Math.max(orderQueueProperties.maxActiveAdmissions() - activeTokens, 0L);
        return (int) Math.min(orderQueueProperties.initialBatchSize(), remainingCapacity);
    }
}
