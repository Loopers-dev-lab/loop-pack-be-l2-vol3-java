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

	private final UserJpaRepository userJpaRepository;

	@Override
	public Optional<User> findByLoginId(String loginId) {
		return userJpaRepository.findByLoginId(loginId)
			.map(UserEntity::toDomain);
	}

	@Override
	public boolean existsByLoginId(String loginId) {
		return userJpaRepository.existsByLoginId(loginId);
	}
}
