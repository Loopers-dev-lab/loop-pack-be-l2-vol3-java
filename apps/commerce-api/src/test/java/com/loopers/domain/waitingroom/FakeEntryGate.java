package com.loopers.domain.waitingroom;

import com.loopers.support.error.CoreException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public class FakeEntryGate implements EntryGate {

    private final Map<Long, String> tokens = new LinkedHashMap<>();

    @Override
    public String issueToken(Long userId) {
        String token = UUID.randomUUID().toString();
        tokens.put(userId, token);
        return token;
    }

    @Override
    public String getToken(Long userId) {
        return tokens.get(userId);
    }

    @Override
    public void validateToken(Long userId, String token) {
        String stored = tokens.get(userId);
        if (stored == null || !stored.equals(token)) {
            throw new CoreException(WaitingRoomErrorCode.INVALID_TOKEN);
        }
    }

    @Override
    public void completeEntry(Long userId) {
        tokens.remove(userId);
    }

    @Override
    public long getActiveCount() {
        return tokens.size();
    }
}
