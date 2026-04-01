package com.loopers.domain.queue;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@RequiredArgsConstructor
@Component
public class TokenService {

    private static final long TOKEN_TTL_SECONDS = 300; // 5분

    private final TokenRepository tokenRepository;

    public String issue(String userId) {
        String token = UUID.randomUUID().toString();
        tokenRepository.save(userId, token, TOKEN_TTL_SECONDS);
        return token;
    }

    public Optional<String> findToken(String userId) {
        return tokenRepository.findToken(userId);
    }

    public boolean isValid(String userId, String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        return tokenRepository.findToken(userId)
                .map(saved -> saved.equals(token))
                .orElse(false);
    }

    public void revoke(String userId) {
        tokenRepository.delete(userId);
    }
}