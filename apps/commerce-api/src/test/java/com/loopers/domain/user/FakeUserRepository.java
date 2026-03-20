package com.loopers.domain.user;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

public class FakeUserRepository implements UserRepository {

    private final Map<String, UserModel> storeByLoginId = new HashMap<>();
    private final Map<Long, UserModel> storeById = new HashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(1);

    @Override
    public UserModel save(UserModel userModel) {
        if (userModel.getId() == null || userModel.getId() == 0L) {
            try {
                var idField = userModel.getClass().getSuperclass().getDeclaredField("id");
                idField.setAccessible(true);
                idField.set(userModel, idGenerator.getAndIncrement());
            } catch (NoSuchFieldException | IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }
        storeByLoginId.put(userModel.getLoginId(), userModel);
        storeById.put(userModel.getId(), userModel);
        return userModel;
    }

    @Override
    public Optional<UserModel> findById(Long id) {
        return Optional.ofNullable(storeById.get(id))
                .filter(user -> user.getDeletedAt() == null);
    }

    @Override
    public Optional<UserModel> findByLoginId(String loginId) {
        return Optional.ofNullable(storeByLoginId.get(loginId));
    }

    @Override
    public Page<UserModel> findAll(Pageable pageable) {
        List<UserModel> activeUsers = storeById.values().stream()
            .filter(user -> user.getDeletedAt() == null)
            .toList();

        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), activeUsers.size());

        List<UserModel> pageContent = start >= activeUsers.size()
            ? new ArrayList<>()
            : activeUsers.subList(start, end);

        return new PageImpl<>(pageContent, pageable, activeUsers.size());
    }

    @Override
    public List<UserModel> findAll() {
        return storeById.values().stream()
            .filter(user -> user.getDeletedAt() == null)
            .toList();
    }
}
