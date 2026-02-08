package com.loopers.user.infrastructure.repository;


import com.loopers.user.application.repository.UserQueryRepository;
import com.loopers.user.domain.model.User;
import com.loopers.user.infrastructure.jpa.UserJpaRepository;
import com.loopers.user.infrastructure.mapper.UserEntityMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;


@Repository
@RequiredArgsConstructor
public class UserQueryRepositoryImpl implements UserQueryRepository {

	// jpa
	private final UserJpaRepository userJpaRepository;
	// mapper
	private final UserEntityMapper userMapper;


	/**
	 * 유저 조회 리포지토리 구현체
	 * 1. 로그인 ID로 유저 조회
	 * 2. 로그인 ID 중복 여부 확인
	 */

	// 1. 로그인 ID로 유저 조회
	@Override
	public Optional<User> findByLoginId(String normalizedLoginId) {
		return userJpaRepository.findByLoginId(normalizedLoginId).map(userMapper::toDomain);
	}


	// 2. 로그인 ID 중복 여부 확인
	@Override
	public boolean existsByLoginId(String normalizedLoginId) {
		return userJpaRepository.existsByLoginId(normalizedLoginId);
	}

}
