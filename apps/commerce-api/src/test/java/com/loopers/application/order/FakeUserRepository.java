package com.loopers.application.order;

import com.loopers.domain.user.User;
import com.loopers.domain.user.UserRepository;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

class FakeUserRepository implements UserRepository {

    private final Map<Long, User> store = new HashMap<>();

    public void addUserWithId(Long id, User user) {
        store.put(id, user);
    }

    @Override
    public Optional<User> findById(Long id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public User save(User user) {
        return user;
    }
}
