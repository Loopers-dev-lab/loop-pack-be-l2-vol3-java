package com.loopers.domain.user;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class InMemoryUserRepository implements UserRepository {
    private final Map<String, User> storeByEmail = new HashMap<>();
    private final Map<String, User> storeByLoginId = new HashMap<>();

    @Override
    public void save(User user) {
        storeByEmail.put(user.getEmail(), user);
        storeByLoginId.put(user.getLoginId(), user);
    }

    @Override
    public User findByEmail(String email) {
        return storeByEmail.get(email);
    }

    @Override
    public Optional<User> findByLoginId(String loginId) {
        return Optional.ofNullable(storeByLoginId.get(loginId));
    }
}
