package com.loopers.domain.user;

import java.util.Optional;

/**
 * 사용자 리포지토리 포트 (Domain Layer)
 *
 * 도메인이 인프라(JPA)에 의존하지 않도록 추상화한 인터페이스.
 * 실제 구현은 Infrastructure 계층의 UserRepositoryImpl이 담당한다.
 */
public interface UserRepository {
    User save(User user);
    Optional<User> findByLoginId(String loginId);
    boolean existsByLoginId(String loginId);
}
