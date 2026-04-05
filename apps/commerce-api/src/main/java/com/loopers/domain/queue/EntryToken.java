package com.loopers.domain.queue;

public record EntryToken(long userId, String token, long expiredAt) {

    public boolean isValid() {
        return System.currentTimeMillis() < expiredAt;
    }
}
