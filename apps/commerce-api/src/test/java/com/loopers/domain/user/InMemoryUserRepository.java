package com.loopers.domain.user;

import java.util.HashMap;
import java.util.Map;

public class InMemoryUserRepository implements UserRepository {
    private final Map<String, User> store = new HashMap<>();

    @Override
    public void save(User user) {
        store.put(user.getEmail(), user);
    }

    @Override
    public User findByEmail(String email) {
        return store.get(email);
    }
}
