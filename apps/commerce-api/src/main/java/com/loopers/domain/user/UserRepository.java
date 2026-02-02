package com.loopers.domain.user;

public interface UserRepository {
    void save(User user);
    User findByEmail(String email);
}
