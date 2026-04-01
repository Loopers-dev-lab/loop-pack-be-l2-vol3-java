package com.loopers.support.entry;

import java.util.Optional;

public interface EntryTokenStore {

    Optional<String> getToken(Long userId);
}
