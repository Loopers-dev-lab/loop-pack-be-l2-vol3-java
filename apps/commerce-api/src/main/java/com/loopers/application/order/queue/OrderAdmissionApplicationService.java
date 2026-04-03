package com.loopers.application.order.queue;

import com.loopers.domain.orderqueue.OrderQueueRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Slf4j
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
            log.info("issueAdmissions skipped batchSize={} activeTokens={} maxActive={}", batchSize, admissionStorageStrategy.countActiveTokens(nowMillis), orderQueueProperties.maxActiveAdmissions());
            return 0;
        }

        final List<String> candidates = orderQueueRepository.peek(batchSize);
        log.info("issueAdmissions candidates.size={} batchSize={}", candidates.size(), batchSize);
        int issuedCount = 0;
        for (String memberId : candidates) {
            if (admissionStorageStrategy.hasValidToken(memberId)) {
                log.info("issueAdmissions memberId={} already admitted", memberId);
                orderQueueRepository.remove(memberId);
                admissionStorageStrategy.clearClaim(memberId);
                continue;
            }

            if (!admissionStorageStrategy.tryClaim(memberId, nowMillis, orderQueueProperties.claimTtlMs())) {
                log.info("issueAdmissions memberId={} claim failed", memberId);
                continue;
            }

            try {
                if (admissionStorageStrategy.countActiveTokens(nowMillis) >= orderQueueProperties.maxActiveAdmissions()) {
                    log.info("issueAdmissions memberId={} cap reached", memberId);
                    break;
                }

                boolean issued = admissionStorageStrategy.issueToken(memberId, nowMillis, orderQueueProperties.tokenTtlMs());
                log.info("issueAdmissions memberId={} issued={}", memberId, issued);
                if (issued) {
                    orderQueueRepository.remove(memberId);
                    issuedCount++;
                }
            } catch (RuntimeException e) {
                log.error("issueAdmissions memberId={} failed", memberId, e);
                throw e;
            } finally {
                try {
                    admissionStorageStrategy.clearClaim(memberId);
                } catch (RuntimeException e) {
                    log.error("issueAdmissions memberId={} clearClaim failed", memberId, e);
                    throw e;
                }
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
