package com.loopers.domain.user;

import java.util.List;
import java.util.Optional;

public interface UserRepository {

    // Command

    User save(User user);

    // Query

    Optional<User> findById(Long id);

    List<User> findAllByIds(List<Long> ids);

    Optional<User> findByLoginId(String loginId);

    boolean existsByLoginId(String loginId);
}
