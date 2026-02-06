package com.loopers.user.application.service;

import com.loopers.user.application.repository.UserQueryRepository;
import com.loopers.user.domain.model.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UserQueryService {

	private final UserQueryRepository userQueryRepository;

	public Optional<User> findByLoginId(String loginId) {
		return userQueryRepository.findByLoginId(loginId);
	}

	public boolean existsByLoginId(String loginId) {
		return userQueryRepository.existsByLoginId(loginId);
	}
}
