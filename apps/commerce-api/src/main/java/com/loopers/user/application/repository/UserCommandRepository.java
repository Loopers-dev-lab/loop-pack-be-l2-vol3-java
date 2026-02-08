package com.loopers.user.application.repository;


import com.loopers.user.domain.model.User;


/**
 * 유저 명령 리포지토리
 * 1. 유저 저장
 * 2. 유저 수정
 */
public interface UserCommandRepository {

	// 1. 유저 저장
	User save(User user);

}
