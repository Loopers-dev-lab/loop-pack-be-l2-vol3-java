package com.loopers.domain.user;

import com.loopers.domain.user.vo.UserId;

import java.util.Optional;

public interface UserRepository {

    Optional<User> findByUserId(UserId userId);

    boolean existsByUserId(UserId userId);

    User save(User user);
}
