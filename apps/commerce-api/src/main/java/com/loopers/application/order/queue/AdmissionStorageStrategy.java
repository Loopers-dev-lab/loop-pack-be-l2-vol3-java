package com.loopers.application.order.queue;

public interface AdmissionStorageStrategy {

    boolean tryClaim(String memberId, long nowMillis, long claimTtlMillis);

    boolean hasValidToken(String memberId);

    boolean hasActiveClaim(String memberId);

    boolean issueToken(String memberId, long nowMillis, long tokenTtlMillis);

    long countActiveTokens(long nowMillis);

    void removeToken(String memberId);

    void clearClaim(String memberId);
}
