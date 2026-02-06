package com.loopers.domain.repository;

import com.loopers.domain.model.User;
import com.loopers.domain.model.UserId;

import java.util.Optional;

public interface UserRepository {

    User save(User member);

    Optional<User> findById(UserId userId);

    boolean existsById(UserId userId);


}
