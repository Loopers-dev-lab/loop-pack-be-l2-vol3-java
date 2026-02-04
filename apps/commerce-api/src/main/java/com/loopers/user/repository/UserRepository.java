package com.loopers.user.repository;

import com.loopers.user.domain.User;

public interface UserRepository {
    User save(User user);

    boolean existsByLoginId(String loginId);
}
