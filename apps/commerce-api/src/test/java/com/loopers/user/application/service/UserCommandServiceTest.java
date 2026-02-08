package com.loopers.user.application.service;

import com.loopers.user.application.repository.UserCommandRepository;
import com.loopers.user.domain.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserCommandService 테스트")
class UserCommandServiceTest {

	@Mock
	private UserCommandRepository userCommandRepository;

	private UserCommandService userCommandService;

	@BeforeEach
	void setUp() {
		userCommandService = new UserCommandService(userCommandRepository);
	}

	@Test
	@DisplayName("[UserCommandService.updateUser()] 변경된 User 저장 -> 저장된 User 반환. "
		+ "repository.update() 위임")
	void updateUserSuccess() {
		// Arrange
		User user = User.reconstruct(
			1L, "testuser01", "encodedPassword", "홍길동",
			LocalDate.of(1990, 1, 15), "test@example.com"
		);

		given(userCommandRepository.update(any(User.class))).willReturn(user);

		// Act
		User result = userCommandService.updateUser(user);

		// Assert
		assertAll(
			() -> assertThat(result).isNotNull(),
			() -> assertThat(result.getId()).isEqualTo(1L),
			() -> assertThat(result.getLoginId()).isEqualTo("testuser01")
		);
		verify(userCommandRepository).update(user);
	}

	@Test
	@DisplayName("[UserCommandService.createUser()] 유효한 User 저장 -> 저장된 User 반환. "
		+ "ID가 할당되어 반환됨")
	void createUserSuccess() {
		// Arrange
		User user = User.create(
			"testuser01",
			"Test1234!",
			"홍길동",
			LocalDate.of(1990, 1, 15),
			"test@example.com"
		);

		given(userCommandRepository.save(any(User.class))).willAnswer(invocation -> {
			User savedUser = invocation.getArgument(0);
			return User.reconstruct(
				1L,
				savedUser.getLoginId(),
				savedUser.getPassword().value(),
				savedUser.getName(),
				savedUser.getBirthday(),
				savedUser.getEmail()
			);
		});

		// Act
		User result = userCommandService.createUser(user);

		// Assert
		assertAll(
			() -> assertThat(result).isNotNull(),
			() -> assertThat(result.getId()).isEqualTo(1L),
			() -> assertThat(result.getLoginId()).isEqualTo("testuser01")
		);
		verify(userCommandRepository).save(any(User.class));
	}
}
