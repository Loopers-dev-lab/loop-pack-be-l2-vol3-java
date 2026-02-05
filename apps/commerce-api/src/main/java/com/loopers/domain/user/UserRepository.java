package com.loopers.domain.user;

import org.springframework.stereotype.Repository;

import java.util.Optional;

public interface UserRepository {

    User save(User user);

    Optional<User> findByLoginId(LoginId loginId);
}
