package com.loopers.user.application.service;

import com.loopers.user.application.repository.UserCommandRepository;
import com.loopers.user.domain.model.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserCommandService {

	private final UserCommandRepository userCommandRepository;

	@Transactional
	public User createUser(User user) {
		return userCommandRepository.save(user);
	}

	@Transactional
	public User updateUser(User user) {
		return userCommandRepository.save(user);
	}
}
