package com.loopers.user.application.service;


import com.loopers.support.common.error.CoreException;
import com.loopers.support.common.error.ErrorType;
import com.loopers.user.application.dto.in.UserSignUpInDto;
import com.loopers.user.application.repository.UserCommandRepository;
import com.loopers.user.domain.model.User;
import com.loopers.user.domain.model.vo.LoginId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service
@RequiredArgsConstructor
public class UserCommandService {

	// repository
	private final UserQueryService userQueryService;
	private final UserCommandRepository userCommandRepository;


	/**
	 * 유저 명령 서비스
	 * 1. 유저 생성
	 * 2. 비밀번호 변경
	 * 3. 유저 인증
	 */

	// 1. 유저 생성
	@Transactional
	public User createUser(UserSignUpInDto inDto) {

		// 유저 도메인 객체 생성
		User user = User.create(
			inDto.loginId(),
			inDto.password(),
			inDto.name(),
			inDto.birthday(),
			inDto.email()
		);

		// 저장 후 반환
		return userCommandRepository.save(user);
	}


	// 2. 비밀번호 변경
	@Transactional
	public void updatePassword(String headerPassword, String rawLoginId, String curPassword, String newPassword) {

		// 유저 인증
		User user = this.authenticate(rawLoginId, headerPassword);

		// 비밀번호 변경 및 저장
		user.changePassword(curPassword, newPassword);
		userCommandRepository.save(user);
	}


	// 3. 유저 인증
	@Transactional
	public User authenticate(String rawLoginId, String password) {

		// 로그인 ID로 유저 조회
		String normalizedLoginId = LoginId.normalize(rawLoginId);
		User user = userQueryService.findByLoginId(normalizedLoginId)
			.orElseThrow(() -> new CoreException(ErrorType.UNAUTHORIZED));

		// 비밀번호 인증
		user.authenticate(password);

		// 유저 반환
		return user;
	}

}
