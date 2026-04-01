package com.loopers.application.queue;

import com.loopers.domain.queue.EntryTokenRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EntryTokenService {

    private final EntryTokenRepository entryTokenRepository;

    public void validate(Long userId, String token) {
        if (token == null) {
            throw new CoreException(ErrorType.ENTRY_TOKEN_REQUIRED);
        }

        if (!entryTokenRepository.validateToken(userId, token)) {
            throw new CoreException(ErrorType.ENTRY_TOKEN_INVALID);
        }
    }

    public void consume(Long userId) {
        entryTokenRepository.deleteToken(userId);
    }
}
