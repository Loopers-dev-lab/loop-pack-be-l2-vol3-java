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
    private final UserMapper userMapper;

    public UserRepositoryImpl(UserJpaRepository userJpaRepository, UserMapper userMapper) {
        this.userJpaRepository = userJpaRepository;
        this.userMapper = userMapper;
    }

    @Override
    public User save(User user) {
        UserEntity entity = userMapper.toEntity(user);
        UserEntity saved = userJpaRepository.save(entity);
        return userMapper.toDomain(saved);
    }

    @Override
    public Optional<User> findByLoginId(String loginId) {
        return userJpaRepository.findByLoginId(loginId)
                .map(userMapper::toDomain);
    }

    @Override
    public boolean existsByLoginId(String loginId) {
        return userJpaRepository.existsByLoginId(loginId);
    }
}
