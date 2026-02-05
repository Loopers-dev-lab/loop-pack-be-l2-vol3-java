package com.loopers.user.infrastructure.repository;

import com.loopers.user.application.repository.UserCommandRepository;
import com.loopers.user.domain.model.User;
import com.loopers.user.infrastructure.entity.UserEntity;
import com.loopers.user.infrastructure.jpa.UserJpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public class UserCommandRepositoryImpl implements UserCommandRepository {

	private final UserJpaRepository userJpaRepository;

	public UserCommandRepositoryImpl(UserJpaRepository userJpaRepository) {
		this.userJpaRepository = userJpaRepository;
	}

	@Override
	public User save(User user) {
		UserEntity entity = UserEntity.from(user);
		UserEntity savedEntity = userJpaRepository.save(entity);
		return savedEntity.toDomain();
	}
}
