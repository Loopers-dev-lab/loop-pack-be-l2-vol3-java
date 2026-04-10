package com.loopers.domain.waitingroom;

public interface EntryGate {

    String issueToken(Long userId);

    String getToken(Long userId);

    void validateToken(Long userId, String token);

    void completeEntry(Long userId);

    long getActiveCount();
}
