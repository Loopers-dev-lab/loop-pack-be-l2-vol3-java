package com.loopers.user.application.service;

import com.loopers.user.application.repository.UserQueryRepository;
import com.loopers.user.domain.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserQueryService 테스트")
class UserQueryServiceTest {

	@Mock
	private UserQueryRepository userQueryRepository;

	private UserQueryService userQueryService;

	@BeforeEach
	void setUp() {
		userQueryService = new UserQueryService(userQueryRepository);
	}

	@Test
	@DisplayName("[UserQueryService.findByLoginId()] 존재하는 loginId -> User를 포함한 Optional 반환")
	void findByLoginIdSuccess() {
		// Arrange
		User user = User.reconstruct(1L, "testuser01", "encodedPw", "홍길동",
			LocalDate.of(1990, 1, 15), "test@example.com");
		given(userQueryRepository.findByLoginId("testuser01")).willReturn(Optional.of(user));

		// Act
		Optional<User> result = userQueryService.findByLoginId("testuser01");

		// Assert
		assertAll(
			() -> assertThat(result).isPresent(),
			() -> assertThat(result.get().getLoginId()).isEqualTo("testuser01")
		);
		verify(userQueryRepository).findByLoginId("testuser01");
	}

	@Test
	@DisplayName("[UserQueryService.findByLoginId()] 존재하지 않는 loginId -> 빈 Optional 반환")
	void findByLoginIdNotFound() {
		// Arrange
		given(userQueryRepository.findByLoginId("nonexistent")).willReturn(Optional.empty());

		// Act
		Optional<User> result = userQueryService.findByLoginId("nonexistent");

		// Assert
		assertThat(result).isEmpty();
		verify(userQueryRepository).findByLoginId("nonexistent");
	}

	@Test
	@DisplayName("[UserQueryService.existsByLoginId()] 존재하는 loginId -> true 반환")
	void existsByLoginIdTrue() {
		// Arrange
		given(userQueryRepository.existsByLoginId("testuser01")).willReturn(true);

		// Act
		boolean result = userQueryService.existsByLoginId("testuser01");

		// Assert
		assertThat(result).isTrue();
		verify(userQueryRepository).existsByLoginId("testuser01");
	}

	@Test
	@DisplayName("[UserQueryService.existsByLoginId()] 존재하지 않는 loginId -> false 반환")
	void existsByLoginIdFalse() {
		// Arrange
		given(userQueryRepository.existsByLoginId("nonexistent")).willReturn(false);

		// Act
		boolean result = userQueryService.existsByLoginId("nonexistent");

		// Assert
		assertThat(result).isFalse();
		verify(userQueryRepository).existsByLoginId("nonexistent");
	}
}
