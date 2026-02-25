package com.loopers.domain.user;

import java.util.Optional;

public interface UserRepository {

    // Command

    User save(User user);

    // Query

    Optional<User> findById(Long id);

    Optional<User> findByLoginId(String loginId);

    boolean existsByLoginId(String loginId);
}
