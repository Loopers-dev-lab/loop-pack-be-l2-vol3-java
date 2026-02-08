package com.loopers.user.application.service;

import com.loopers.user.application.repository.UserQueryRepository;
import com.loopers.user.domain.model.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UserQueryService {

	// repository
	private final UserQueryRepository userQueryRepository;

	/**
	 * 유저 조회 서비스
	 * 1. 로그인 ID로 유저 조회
	 * 2. 로그인 ID 중복 여부 확인
	 */

	// 1. 로그인 ID로 유저 조회
	public Optional<User> findByLoginId(String loginId) {
		return userQueryRepository.findByLoginId(loginId);
	}

	// 2. 로그인 ID 중복 여부 확인
	public boolean existsByLoginId(String loginId) {
		return userQueryRepository.existsByLoginId(loginId);
	}
}
