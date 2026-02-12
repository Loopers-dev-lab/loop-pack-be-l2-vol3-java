package com.loopers.domain.user;

import java.util.Optional;

public interface UserRepository {
    void save(User user);
    Optional<User> findByLoginId(String loginId);
}
