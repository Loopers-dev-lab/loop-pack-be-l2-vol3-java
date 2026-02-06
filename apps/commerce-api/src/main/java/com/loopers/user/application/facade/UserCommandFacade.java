package com.loopers.user.application.facade;

import com.loopers.support.common.error.CoreException;
import com.loopers.support.common.error.ErrorType;
import com.loopers.user.application.dto.in.UserChangePasswordInDto;
import com.loopers.user.application.dto.in.UserSignUpInDto;
import com.loopers.user.application.dto.out.UserSignUpOutDto;
import com.loopers.user.application.service.UserCommandService;
import com.loopers.user.application.service.UserQueryService;
import com.loopers.user.domain.model.User;
import com.loopers.user.support.common.HeaderValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserCommandFacade {

	private final UserCommandService userCommandService;
	private final UserQueryService userQueryService;

	@Transactional
	public UserSignUpOutDto signUp(UserSignUpInDto inDto) {
		String normalizedLoginId = inDto.loginId() != null
			? inDto.loginId().trim().toLowerCase()
			: inDto.loginId();

		if (userQueryService.existsByLoginId(normalizedLoginId)) {
			throw new CoreException(ErrorType.USER_ALREADY_EXISTS);
		}

		User user = User.create(
			inDto.loginId(),
			inDto.password(),
			inDto.name(),
			inDto.birthday(),
			inDto.email()
		);

		User savedUser = userCommandService.createUser(user);
		return UserSignUpOutDto.from(savedUser);
	}

	@Transactional
	public void changePassword(String loginId, String headerPassword, UserChangePasswordInDto inDto) {
		HeaderValidator.validate(loginId, headerPassword);

		User user = userQueryService.findByLoginId(loginId.trim())
			.orElseThrow(() -> new CoreException(ErrorType.UNAUTHORIZED));

		user.authenticate(headerPassword);
		user.changePassword(inDto.currentPassword(), inDto.newPassword());

		userCommandService.updateUser(user);
	}
}
