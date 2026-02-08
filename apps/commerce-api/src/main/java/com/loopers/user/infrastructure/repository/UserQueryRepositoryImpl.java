package com.loopers.user.infrastructure.repository;

import com.loopers.user.application.repository.UserQueryRepository;
import com.loopers.user.domain.model.User;
import com.loopers.user.infrastructure.entity.UserEntity;
import com.loopers.user.infrastructure.jpa.UserJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class UserQueryRepositoryImpl implements UserQueryRepository {

	// jpa
	private final UserJpaRepository userJpaRepository;


	/**
	 * 유저 조회 리포지토리 구현체
	 * 1. 로그인 ID로 유저 조회
	 * 2. 로그인 ID 중복 여부 확인
	 */

	// 1. 로그인 ID로 유저 조회
	@Override
	public Optional<User> findByLoginId(String loginId) {

		// 조회 결과를 유저 도메인 객체로 변환
		return userJpaRepository.findByLoginId(loginId)
			.map(UserEntity::toDomain);
	}

	// 2. 로그인 ID 중복 여부 확인
	@Override
	public boolean existsByLoginId(String loginId) {
		return userJpaRepository.existsByLoginId(loginId);
	}
}
