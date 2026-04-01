package com.loopers.domain.queue;

import com.loopers.config.QueueProperties;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EntryTokenService {

    private final EntryTokenRepository entryTokenRepository;
    private final QueueProperties queueProperties;

    public String issue(Long memberId) {
        String token = UUID.randomUUID().toString();
        entryTokenRepository.issue(memberId, token, queueProperties.tokenTtlSeconds());
        entryTokenRepository.recordIssuedAt(memberId, queueProperties.tokenTtlSeconds());
        return token;
    }

    public Optional<String> findToken(Long memberId) {
        return entryTokenRepository.findToken(memberId);
    }

    public void validate(Long memberId, String token) {
        Optional<String> storedToken = entryTokenRepository.findToken(memberId);
        if (storedToken.isEmpty()) {
            throw new CoreException(ErrorType.QUEUE_TOKEN_REQUIRED);
        }
        if (!storedToken.get().equals(token)) {
            throw new CoreException(ErrorType.QUEUE_TOKEN_INVALID);
        }
        validateMinInterval(memberId);
    }

    private void validateMinInterval(Long memberId) {
        int minIntervalSeconds = queueProperties.tokenMinIntervalSeconds();
        if (minIntervalSeconds <= 0) {
            return;
        }
        Optional<Long> issuedAt = entryTokenRepository.findIssuedAt(memberId);
        if (issuedAt.isPresent()) {
            long elapsedMs = System.currentTimeMillis() - issuedAt.get();
            if (elapsedMs < minIntervalSeconds * 1000L) {
                throw new CoreException(ErrorType.QUEUE_TOKEN_TOO_EARLY);
            }
        }
    }

    public void consume(Long memberId) {
        entryTokenRepository.consume(memberId);
    }
}
