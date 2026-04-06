package com.loopers.fake;

import com.loopers.application.queue.TokenService;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class FakeTokenService extends TokenService {

    private final Map<Long, String> tokens = new HashMap<>();

    public FakeTokenService() {
        super(null, null);
    }

    @Override
    public String issueToken(Long userId) {
        String token = UUID.randomUUID().toString();
        tokens.put(userId, token);
        return token;
    }

    @Override
    public boolean validateToken(Long userId, String token) {
        if (token == null) {
            return false;
        }
        String stored = tokens.get(userId);
        return token.equals(stored);
    }

    @Override
    public void deleteToken(Long userId) {
        tokens.remove(userId);
    }

    @Override
    public boolean hasToken(Long userId) {
        return tokens.containsKey(userId);
    }

    @Override
    public Optional<String> getToken(Long userId) {
        return Optional.ofNullable(tokens.get(userId));
    }

    public void clear() {
        tokens.clear();
    }
}