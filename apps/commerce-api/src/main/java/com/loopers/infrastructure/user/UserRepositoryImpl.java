package com.loopers.infrastructure.user;

import com.loopers.domain.user.User;
import com.loopers.domain.user.UserRepository;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class UserRepositoryImpl implements UserRepository {
    private final UserJpaRepository userJpaRepository;

    public UserRepositoryImpl(UserJpaRepository userJpaRepository) {
        this.userJpaRepository = userJpaRepository;
    }

    @Override
    public User save(User user) {
        return this.userJpaRepository.save(user);
    }

    @Override
    public Optional<User> findByLoginId(String loginId) {
        return this.userJpaRepository.findByLoginIdValue(loginId);
    }

    @Override
    public boolean existsByLoginId(String loginId) {
        return this.userJpaRepository.existsByLoginIdValue(loginId);
    }
}
