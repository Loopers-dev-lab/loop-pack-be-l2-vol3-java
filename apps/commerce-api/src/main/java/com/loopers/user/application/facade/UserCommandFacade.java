package com.loopers.user.application.facade;


import com.loopers.support.common.error.CoreException;
import com.loopers.support.common.error.ErrorType;
import com.loopers.user.application.dto.in.UserChangePasswordInDto;
import com.loopers.user.application.dto.in.UserSignUpInDto;
import com.loopers.user.application.dto.out.UserSignUpOutDto;
import com.loopers.user.application.service.UserCommandService;
import com.loopers.user.application.service.UserQueryService;
import com.loopers.user.domain.model.User;
import com.loopers.user.domain.model.vo.LoginId;
import com.loopers.user.support.common.HeaderValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service
@RequiredArgsConstructor
public class UserCommandFacade {

	// service
	private final UserCommandService userCommandService;
	private final UserQueryService userQueryService;


	/**
	 * 유저 명령 파사드
	 * 1. 회원가입
	 * 2. 내 비밀번호 변경
	 */

	// 1. 회원가입
	@Transactional
	public UserSignUpOutDto signUp(UserSignUpInDto inDto) {

		// 로그인 ID 생성 (정규화 & 검증)
		LoginId loginId = LoginId.create(inDto.loginId());

		// 로그인 ID 중복 확인
		if (userQueryService.existsByLoginId(loginId.value())) {
			throw new CoreException(ErrorType.USER_ALREADY_EXISTS);
		}

		// 유저 생성
		User savedUser = userCommandService.createUser(inDto);

		// 결과 응답 DTO로 변환
		return UserSignUpOutDto.from(savedUser);
	}


	// 2. 내 비밀번호 변경
	@Transactional
	public void changePassword(String rawLoginId, String headerPassword, UserChangePasswordInDto inDto) {

		// 인증 헤더 필수값 검증
		HeaderValidator.validate(rawLoginId, headerPassword);

		// 비밀번호 변경
		userCommandService.updatePassword(headerPassword, rawLoginId, inDto.currentPassword(), inDto.newPassword());
	}

}
