package com.loopers.domain.user;

import java.util.Optional;

public interface UserRepository {
    Optional<UserModel> findByLoginId(String loginId);
    UserModel save(UserModel userModel);
}
