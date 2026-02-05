package com.loopers.domain;

import java.util.Optional;

public interface UserRepository {
    UserModel save(UserModel userModel);

    Optional<UserModel> find(LoginId loginId);
}
