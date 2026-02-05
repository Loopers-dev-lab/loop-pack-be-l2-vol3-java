package com.loopers.domain;

import java.util.Optional;

public interface UserRepository {

    UserModel save(UserModel userModel);

    Boolean existsByEmail(String email);
}
