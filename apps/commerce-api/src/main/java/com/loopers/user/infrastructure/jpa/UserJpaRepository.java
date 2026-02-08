package com.loopers.user.infrastructure.jpa;

import com.loopers.user.infrastructure.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 유저 JPA 리포지토리
 * 1. 로그인 ID로 유저 엔티티 조회
 * 2. 로그인 ID 중복 여부 확인
 */
public interface UserJpaRepository extends JpaRepository<UserEntity, Long> {

	// 1. 로그인 ID로 유저 엔티티 조회
	Optional<UserEntity> findByLoginId(String loginId);

	// 2. 로그인 ID 중복 여부 확인
	boolean existsByLoginId(String loginId);
}
