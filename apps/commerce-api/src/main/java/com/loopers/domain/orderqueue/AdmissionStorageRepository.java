package com.loopers.domain.orderqueue;

public interface AdmissionStorageRepository {

    boolean tryClaim(String memberId, long nowMillis, long claimTtlMillis);

    boolean hasValidToken(String memberId);

    boolean issueToken(String memberId, long nowMillis, long tokenTtlMillis);

    long countActiveTokens(long nowMillis);

    void removeToken(String memberId);

    void clearClaim(String memberId);
}
