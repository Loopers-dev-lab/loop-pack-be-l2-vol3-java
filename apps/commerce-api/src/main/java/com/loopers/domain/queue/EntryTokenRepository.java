package com.loopers.domain.queue;

import java.util.Optional;

public interface EntryTokenRepository {

    void saveEntryToken(Long userId, String token, long ttlSeconds);

    Optional<String> findEntryToken(Long userId);

    void deleteEntryToken(Long userId);

    /**
     * 저장된 토큰과 {@code presentedToken}이 같을 때만 키를 삭제한다. 원자적으로 수행한다.
     *
     * @return 일치해 삭제했으면 true, 키 없음·불일치면 false
     */
    boolean consumeIfTokenMatches(Long userId, String presentedToken);
}

