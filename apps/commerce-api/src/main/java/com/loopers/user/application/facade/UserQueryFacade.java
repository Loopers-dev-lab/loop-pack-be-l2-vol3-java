package com.loopers.user.application.facade;


import com.loopers.user.application.dto.out.UserMeOutDto;
import com.loopers.user.application.service.UserCommandService;
import com.loopers.user.application.service.UserQueryService;
import com.loopers.user.domain.model.User;
import com.loopers.user.support.common.HeaderValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service
@RequiredArgsConstructor
public class UserQueryFacade {

	// service
	private final UserQueryService userQueryService;
	private final UserCommandService userCommandService;


	/**
	 * 유저 조회 파사드
	 * 1. 내 정보 조회
	 */

	// 1. 내 정보 조회
	@Transactional(readOnly = true)
	public UserMeOutDto getMe(String rawLoginId, String password) {

		// 인증 헤더 필수값 검증
		HeaderValidator.validate(rawLoginId, password);

		// 유저 인증
		User user = userCommandService.authenticate(rawLoginId, password);

		// 조회 결과를 응답 DTO로 변환
		return UserMeOutDto.from(user);
	}

}
