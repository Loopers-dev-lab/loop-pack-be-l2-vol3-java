package com.loopers.infrastructure.user;

import com.loopers.domain.user.User;
import com.loopers.domain.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * UserRepository의 JPA 어댑터 구현
 *
 * DIP (Dependency Inversion Principle) 구현:
 * - Infrastructure Layer에 위치
 * - Domain Layer의 UserRepository 인터페이스 구현 (Port)
 * - UserJpaRepository (Spring Data JPA)를 사용하여 실제 구현
 *
 * 의존성 방향:
 * Infrastructure (이 클래스) → Domain (UserRepository 인터페이스)
 *
 * 이것이 DIP의 핵심입니다!
 * - 고수준 모듈(Domain)이 저수준 모듈(Infrastructure)에 의존하지 않음
 * - 둘 다 추상화(UserRepository 인터페이스)에 의존
 */
@Repository
@RequiredArgsConstructor
public class UserRepositoryAdapter implements UserRepository {

    private final UserJpaRepository userJpaRepository;

    @Override
    public User save(User user) {
        return userJpaRepository.save(user);
    }

    @Override
    public Optional<User> findById(Long id) {
        return userJpaRepository.findById(id);
    }

    @Override
    public Optional<User> findByLoginId(String loginId) {
        return userJpaRepository.findByLoginId(loginId);
    }

    @Override
    public boolean existsByLoginId(String loginId) {
        return userJpaRepository.existsByLoginId(loginId);
    }
}
