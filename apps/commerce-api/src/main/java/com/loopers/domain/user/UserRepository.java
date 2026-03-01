package com.loopers.domain.user;

import java.util.List;
import java.util.Optional;

public interface UserRepository {

    User save(User user);

    Optional<User> findById(Long id);

    List<User> findAllByIdIn(List<Long> ids);

    Optional<User> findByLoginId(LoginId loginId);

    boolean existsByLoginId(LoginId loginId);
}
