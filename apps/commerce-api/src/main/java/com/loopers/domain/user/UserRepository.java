package com.loopers.domain.user;

import java.util.Optional;

public interface UserRepository {
    void save(User user);
    User findByEmail(String email);
    Optional<User> findByLoginId(String loginId);
}
