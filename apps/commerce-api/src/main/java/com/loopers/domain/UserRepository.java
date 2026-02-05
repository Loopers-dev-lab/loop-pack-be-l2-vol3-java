package com.loopers.domain;

public interface UserRepository {

    UserModel save(UserModel userModel);

    Boolean existsByEmail(String email);
}
