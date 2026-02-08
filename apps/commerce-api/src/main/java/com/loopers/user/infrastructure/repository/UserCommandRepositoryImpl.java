package com.loopers.user.infrastructure.repository;


import com.loopers.user.application.repository.UserCommandRepository;
import com.loopers.user.domain.model.User;
import com.loopers.user.infrastructure.entity.UserEntity;
import com.loopers.user.infrastructure.jpa.UserJpaRepository;
import com.loopers.user.infrastructure.mapper.UserEntityMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;


@Repository
@RequiredArgsConstructor
public class UserCommandRepositoryImpl implements UserCommandRepository {

	// jpa
	private final UserJpaRepository userJpaRepository;
	private final UserEntityMapper userMapper;


	/**
	 * 유저 명령 리포지토리 구현체
	 * 1. 유저 저장
	 * 2. 유저 수정
	 */

	// 1. 유저 저장
	@Override
	public User save(User user) {

		// mapping
		UserEntity entity = userMapper.toEntity(user);

		// 엔티티 저장 후 도메인 객체로 변환
		return userMapper.toDomain(userJpaRepository.save(entity));
	}

}
