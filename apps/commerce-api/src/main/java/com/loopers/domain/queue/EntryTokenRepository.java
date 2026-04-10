package com.loopers.domain.queue;

import java.util.Optional;

public interface EntryTokenRepository {

    void save(EntryToken token);

    Optional<EntryToken> findByUserId(Long userId);

    void deleteByUserId(Long userId);

    long countActive();
}
