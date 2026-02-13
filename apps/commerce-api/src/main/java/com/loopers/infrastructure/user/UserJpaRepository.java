package com.loopers.infrastructure.user;

import com.loopers.domain.user.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 사용자 JPA 리포지토리
 *
 * Spring Data JPA가 제공하는 기본 CRUD와 쿼리 메서드를 정의한다.
 * 도메인 계층의 {@link com.loopers.domain.user.UserRepository} 포트 구현체인
 * {@link UserRepositoryImpl}이 이 인터페이스에 위임하여 실제 DB 접근을 수행한다.
 */
public interface UserJpaRepository extends JpaRepository<User, Long> {
    Optional<User> findByLoginIdValue(String loginId);
    boolean existsByLoginIdValue(String loginId);
}
