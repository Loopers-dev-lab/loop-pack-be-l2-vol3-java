package com.loopers.user.application.facade;

import com.loopers.support.common.error.CoreException;
import com.loopers.support.common.error.ErrorType;
import com.loopers.user.application.dto.in.UserChangePasswordInDto;
import com.loopers.user.application.dto.in.UserSignUpInDto;
import com.loopers.user.application.dto.out.UserSignUpOutDto;
import com.loopers.user.application.service.UserCommandService;
import com.loopers.user.application.service.UserQueryService;
import com.loopers.user.domain.model.User;
import com.loopers.user.domain.service.LoginIdDuplicateValidator;
import com.loopers.user.support.common.HeaderValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserCommandFacade {

	// service
	private final UserCommandService userCommandService;
	// service
	private final UserQueryService userQueryService;
	// service
	private final LoginIdDuplicateValidator loginIdDuplicateValidator;


	/**
	 * 유저 명령 파사드
	 * 1. 회원가입
	 * 2. 내 비밀번호 변경
	 */

	// 1. 회원가입
	@Transactional
	public UserSignUpOutDto signUp(UserSignUpInDto inDto) {

		// 로그인 ID 중복 여부 검증
		loginIdDuplicateValidator.validate(inDto.loginId());

		// 회원가입 입력값으로 유저 도메인 객체 생성
		User user = User.create(
			inDto.loginId(),
			inDto.password(),
			inDto.name(),
			inDto.birthday(),
			inDto.email()
		);

		// 유저 저장 후 회원가입 결과 DTO로 변환
		User savedUser = userCommandService.createUser(user);
		return UserSignUpOutDto.from(savedUser);
	}

	// 2. 내 비밀번호 변경
	@Transactional
	public void changePassword(String loginId, String headerPassword, UserChangePasswordInDto inDto) {

		// 인증 헤더 필수값 검증
		HeaderValidator.validate(loginId, headerPassword);

		// 로그인 ID로 유저 조회
		User user = userQueryService.findByLoginId(loginId.trim())
			.orElseThrow(() -> new CoreException(ErrorType.UNAUTHORIZED));

		// 현재 비밀번호 인증 후 새 비밀번호로 변경
		user.authenticate(headerPassword);
		user.changePassword(inDto.currentPassword(), inDto.newPassword());

		// 변경된 유저 비밀번호 저장
		userCommandService.updateUser(user);
	}
}
