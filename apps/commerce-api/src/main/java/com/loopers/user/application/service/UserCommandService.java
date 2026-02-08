package com.loopers.user.application.service;

import com.loopers.user.application.repository.UserCommandRepository;
import com.loopers.user.domain.model.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserCommandService {

	// repository
	private final UserCommandRepository userCommandRepository;


	/**
	 * 유저 명령 서비스
	 * 1. 유저 저장
	 * 2. 유저 수정
	 */

	// 1. 유저 저장
	@Transactional
	public User createUser(User user) {
		return userCommandRepository.save(user);
	}

	// 2. 유저 수정
	@Transactional
	public User updateUser(User user) {
		return userCommandRepository.update(user);
	}
}
