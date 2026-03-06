package com.loopers.domain.user;

import java.util.Optional;

public interface UserRepository {

    Optional<User> findById(Long id);

    Optional<User> findByIdWithLock(Long id);

    User save(User user);
}
