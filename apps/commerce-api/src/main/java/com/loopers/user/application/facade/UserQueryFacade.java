package com.loopers.user.application.facade;


import com.loopers.support.common.error.CoreException;
import com.loopers.support.common.error.ErrorType;
import com.loopers.user.application.dto.out.UserMeOutDto;
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


	/**
	 * 유저 조회 파사드
	 * 1. 내 정보 조회
	 */

	// 1. 내 정보 조회
	@Transactional(readOnly = true)
	public UserMeOutDto getMe(String loginId, String password) {

		// 인증 헤더 필수값 검증
		HeaderValidator.validate(loginId, password);

		// 로그인 ID로 유저 조회
		String trimmedLoginId = loginId.trim();
		User user = userQueryService.findByLoginId(trimmedLoginId)
			.orElseThrow(() -> new CoreException(ErrorType.UNAUTHORIZED));

		// 비밀번호 인증
		user.authenticate(password);

		// 조회 결과를 응답 DTO로 변환
		return UserMeOutDto.from(user);
	}

}
