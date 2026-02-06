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

	private final UserQueryService userQueryService;

	@Transactional(readOnly = true)
	public UserMeOutDto getMe(String loginId, String password) {
		HeaderValidator.validate(loginId, password);

		String trimmedLoginId = loginId.trim();
		User user = userQueryService.findByLoginId(trimmedLoginId)
			.orElseThrow(() -> new CoreException(ErrorType.UNAUTHORIZED));

		user.authenticate(password);

		return UserMeOutDto.from(user);
	}
}
