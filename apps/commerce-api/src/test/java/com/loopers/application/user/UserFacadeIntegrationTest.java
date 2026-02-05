package com.loopers.application.user;

import com.loopers.application.auth.AuthFacade;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.UserErrorType;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * [Application Layer - UserFacade 통합 테스트]
 *
 * UserFacade의 비즈니스 흐름을 검증하는 통합 테스트.
 * Spring Context를 로드하고 실제 DB를 사용하여 오케스트레이션 로직을 검증한다.
 *
 * 테스트 범위:
 * - UserFacade → UserService → UserRepository → DB
 * - 실제 비즈니스 흐름 전체 검증
 */
@SpringBootTest
class UserFacadeIntegrationTest {

    @Autowired
    private UserFacade userFacade;

    @Autowired
    private AuthFacade authFacade;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @Nested
    @DisplayName("getMyInfo 메서드는")
    class GetMyInfo {

        @Test
        @DisplayName("유효한 인증 정보면, 사용자 정보를 반환한다")
        void returnsUserInfoWhenValidCredentials() {
            // arrange
            String loginId = "nahyeon";
            String password = "Hx7!mK2@";
            authFacade.signup(loginId, password, "홍길동", "1994-11-15", "nahyeon@example.com");

            // act
            UserInfo result = userFacade.getMyInfo(loginId, password);

            // assert
            assertAll(
                    () -> assertThat(result.loginId()).isEqualTo("nahyeon"),
                    () -> assertThat(result.name()).isEqualTo("홍길동"),
                    () -> assertThat(result.maskedName()).isEqualTo("홍길*"),
                    () -> assertThat(result.birthDate()).isEqualTo(LocalDate.of(1994, 11, 15)),
                    () -> assertThat(result.email()).isEqualTo("nahyeon@example.com")
            );
        }

        @Test
        @DisplayName("존재하지 않는 사용자면, 예외가 발생한다")
        void throwsExceptionWhenUserNotFound() {
            // arrange
            String loginId = "nonexistent";
            String password = "Hx7!mK2@";

            // act & assert
            CoreException exception = assertThrows(CoreException.class, () -> {
                userFacade.getMyInfo(loginId, password);
            });

            assertThat(exception.getErrorType()).isEqualTo(UserErrorType.UNAUTHORIZED);
        }

        @Test
        @DisplayName("비밀번호가 틀리면, 예외가 발생한다")
        void throwsExceptionWhenPasswordWrong() {
            // arrange
            String loginId = "nahyeon";
            String password = "Hx7!mK2@";
            authFacade.signup(loginId, password, "홍길동", "1994-11-15", "nahyeon@example.com");

            // act & assert
            CoreException exception = assertThrows(CoreException.class, () -> {
                userFacade.getMyInfo(loginId, "wrongPw1!");
            });

            assertThat(exception.getErrorType()).isEqualTo(UserErrorType.UNAUTHORIZED);
        }
    }
}