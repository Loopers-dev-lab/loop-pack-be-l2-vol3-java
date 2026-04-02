package com.loopers.application.order.queue;

import com.loopers.domain.orderqueue.AdmissionStorageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "loopers.queue.order", name = "admission-strategy", havingValue = "key-ttl", matchIfMissing = true)
public class KeyTtlAdmissionStorageStrategy implements AdmissionStorageStrategy {

    private final AdmissionStorageRepository admissionStorageRepository;

    @Override
    public boolean tryClaim(String memberId, long nowMillis, long claimTtlMillis) {
        return admissionStorageRepository.tryClaim(memberId, nowMillis, claimTtlMillis);
    }

    @Override
    public boolean hasValidToken(String memberId) {
        return admissionStorageRepository.hasValidToken(memberId);
    }

    @Override
    public boolean issueToken(String memberId, long nowMillis, long tokenTtlMillis) {
        return admissionStorageRepository.issueToken(memberId, nowMillis, tokenTtlMillis);
    }

    @Override
    public long countActiveTokens(long nowMillis) {
        return admissionStorageRepository.countActiveTokens(nowMillis);
    }

    @Override
    public void removeToken(String memberId) {
        admissionStorageRepository.removeToken(memberId);
    }

    @Override
    public void clearClaim(String memberId) {
        admissionStorageRepository.clearClaim(memberId);
    }
}
