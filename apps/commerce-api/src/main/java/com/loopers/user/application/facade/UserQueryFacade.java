package com.loopers.user.application.facade;

import com.loopers.support.common.error.CoreException;
import com.loopers.support.common.error.ErrorType;
import com.loopers.user.application.repository.UserQueryRepository;
import com.loopers.user.domain.model.User;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class UserQueryFacade {

	private final UserQueryRepository userQueryRepository;

	public UserQueryFacade(UserQueryRepository userQueryRepository) {
		this.userQueryRepository = userQueryRepository;
	}

	@Transactional(readOnly = true)
	public User getMe(String loginId, String password) {
		validateHeaders(loginId, password);

		String trimmedLoginId = loginId.trim();
		User user = userQueryRepository.findByLoginId(trimmedLoginId)
			.orElseThrow(() -> new CoreException(ErrorType.UNAUTHORIZED));

		user.authenticate(password);

		return user;
	}

	private void validateHeaders(String loginId, String password) {
		if (loginId == null || loginId.isBlank()) {
			throw new CoreException(ErrorType.UNAUTHORIZED);
		}
		if (password == null || password.isBlank()) {
			throw new CoreException(ErrorType.UNAUTHORIZED);
		}
	}
}
