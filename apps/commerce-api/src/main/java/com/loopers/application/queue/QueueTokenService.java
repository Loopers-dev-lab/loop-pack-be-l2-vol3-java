package com.loopers.application.queue;

import com.loopers.domain.queue.QueueTokenRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@RequiredArgsConstructor
@Component
public class QueueTokenService {

    private static final long TOKEN_TTL_SECONDS = 300;

    private final QueueTokenRepository queueTokenRepository;

    public String issueToken(String eventId, Long userId) {
        String token = UUID.randomUUID().toString();
        queueTokenRepository.issueToken(eventId, userId, token, TOKEN_TTL_SECONDS);
        return token;
    }

    public Optional<TokenInfo> getTokenInfo(String eventId, Long userId) {
        return queueTokenRepository.getToken(eventId, userId)
                .map(token -> new TokenInfo(token, queueTokenRepository.getTokenTtl(eventId, userId)));
    }

    public void validateToken(String eventId, Long userId, String token) {
        String storedToken = queueTokenRepository.getToken(eventId, userId)
                .orElseThrow(() -> new CoreException(ErrorType.UNAUTHORIZED, "토큰이 존재하지 않습니다."));

        if (!storedToken.equals(token)) {
            throw new CoreException(ErrorType.UNAUTHORIZED, "토큰이 일치하지 않습니다.");
        }
    }

    public void removeToken(String eventId, Long userId) {
        queueTokenRepository.removeToken(eventId, userId);
    }

    public record TokenInfo(String token, long expiresIn) {}
}
