package com.loopers.infrastructure.user;

import com.loopers.domain.user.User;
import com.loopers.domain.user.UserRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * UserRepository 어댑터 (Infrastructure Layer)
 *
 * 도메인 포트(UserRepository)의 구현체로, Spring Data JPA에 위임한다.
 */
@Repository
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
