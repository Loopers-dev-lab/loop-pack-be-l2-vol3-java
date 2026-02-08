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

	// jpa
	private final UserJpaRepository userJpaRepository;


	/**
	 * 유저 명령 리포지토리 구현체
	 * 1. 유저 저장
	 * 2. 유저 수정
	 */

	// 1. 유저 저장
	@Override
	public User save(User user) {

		// 도메인 객체를 엔티티로 변환 후 저장
		UserEntity entity = UserEntity.from(user);
		UserEntity savedEntity = userJpaRepository.save(entity);
		return savedEntity.toDomain();
	}

	// 2. 유저 수정
	@Override
	public User update(User user) {

		// 수정 대상 유저 ID 유효성 검증
		if (user.getId() == null) {
			throw new CoreException(ErrorType.BAD_REQUEST);
		}

		// 기존 엔티티 조회 후 비밀번호 갱신
		UserEntity existingEntity = userJpaRepository.findById(user.getId())
			.orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND));
		existingEntity.updatePassword(user.getPassword().value());

		// 갱신 엔티티 저장 후 도메인 객체로 변환
		UserEntity updatedEntity = userJpaRepository.save(existingEntity);
		return updatedEntity.toDomain();
	}
}
