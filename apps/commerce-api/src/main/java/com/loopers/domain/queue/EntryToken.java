package com.loopers.domain.queue;

import java.util.Objects;

public record EntryToken(
    Long userId,
    String token,
    long activateAt
) {
    public EntryToken {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(token, "token must not be null");
    }

    public boolean isActivated(long now) {
        return now >= activateAt;
    }
}
