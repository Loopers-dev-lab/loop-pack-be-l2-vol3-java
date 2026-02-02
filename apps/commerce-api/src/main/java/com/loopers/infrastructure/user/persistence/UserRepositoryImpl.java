package com.loopers.infrastructure.user.persistence;

import java.util.Optional;

import org.springframework.stereotype.Component;

import com.loopers.domain.user.LoginId;
import com.loopers.domain.user.User;
import com.loopers.domain.user.UserRepository;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class UserRepositoryImpl implements UserRepository {

    private final UserJpaRepository userJpaRepository;

    @Override
    public User save(User user) {
        return userJpaRepository.save(user);
    }

    @Override
    public Optional<User> findByLoginId(LoginId loginId) {
        return userJpaRepository.findByLoginId(loginId);
    }

    @Override
    public boolean existsByLoginId(LoginId loginId) {
        return userJpaRepository.existsByLoginId(loginId);
    }
}
