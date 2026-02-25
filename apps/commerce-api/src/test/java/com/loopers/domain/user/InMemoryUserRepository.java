package com.loopers.domain.user;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

public class InMemoryUserRepository implements UserRepository {
    private final Map<String, User> storeByEmail = new HashMap<>();
    private final Map<String, User> storeByLoginId = new HashMap<>();
    private final Map<Long, User> storeById = new HashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(1);

    @Override
    public void save(User user) {
        if (user.getId() == 0L) {
            try {
                var idField = user.getClass().getSuperclass().getDeclaredField("id");
                idField.setAccessible(true);
                idField.set(user, idGenerator.getAndIncrement());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        storeByEmail.put(user.getEmail(), user);
        storeByLoginId.put(user.getLoginId(), user);
        storeById.put(user.getId(), user);
    }

    @Override
    public Optional<User> findByLoginId(String loginId) {
        return Optional.ofNullable(storeByLoginId.get(loginId));
    }

    @Override
    public Optional<User> findById(Long id) {
        return Optional.ofNullable(storeById.get(id));
    }
}
