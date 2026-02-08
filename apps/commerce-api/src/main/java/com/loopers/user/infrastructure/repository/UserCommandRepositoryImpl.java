package com.loopers.user.infrastructure.repository;

import com.loopers.support.common.error.CoreException;
import com.loopers.support.common.error.ErrorType;
import com.loopers.user.application.repository.UserCommandRepository;
import com.loopers.user.domain.model.User;
import com.loopers.user.infrastructure.entity.UserEntity;
import com.loopers.user.infrastructure.jpa.UserJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class UserCommandRepositoryImpl implements UserCommandRepository {

	private final UserJpaRepository userJpaRepository;

	@Override
	public User save(User user) {

		UserEntity entity = UserEntity.from(user);
		UserEntity savedEntity = userJpaRepository.save(entity);
		return savedEntity.toDomain();
	}

	@Override
	public User update(User user) {

		if (user.getId() == null) {
			throw new CoreException(ErrorType.BAD_REQUEST);
		}

		UserEntity existingEntity = userJpaRepository.findById(user.getId())
			.orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND));
		existingEntity.updatePassword(user.getPassword().value());

		UserEntity updatedEntity = userJpaRepository.save(existingEntity);
		return updatedEntity.toDomain();
	}
}
