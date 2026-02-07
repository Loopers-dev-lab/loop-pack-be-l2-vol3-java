package com.loopers.application.user;

import com.loopers.application.auth.AuthFacade;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.UserErrorType;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
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
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
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
    @DisplayName("getUser 메서드는")
    class GetMyInfo {

        @Test
        void 유효한_인증_정보면_사용자_정보를_반환한다() {
            // arrange
            String loginId = "nahyeon";
            String password = "Hx7!mK2@";
            authFacade.createUser(loginId, password, "홍길동", "1994-11-15", "nahyeon@example.com");

            // act
            UserInfo result = userFacade.getUser(loginId, password);

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
        void 존재하지_않는_사용자면_예외가_발생한다() {
            // arrange
            String loginId = "nonexistent";
            String password = "Hx7!mK2@";

            // act & assert
            CoreException exception = assertThrows(CoreException.class, () -> {
                userFacade.getUser(loginId, password);
            });

            assertThat(exception.getErrorType()).isEqualTo(UserErrorType.UNAUTHORIZED);
        }

        @Test
        void 비밀번호가_틀리면_예외가_발생한다() {
            // arrange
            String loginId = "nahyeon";
            String password = "Hx7!mK2@";
            authFacade.createUser(loginId, password, "홍길동", "1994-11-15", "nahyeon@example.com");

            // act & assert
            CoreException exception = assertThrows(CoreException.class, () -> {
                userFacade.getUser(loginId, "wrongPw1!");
            });

            assertThat(exception.getErrorType()).isEqualTo(UserErrorType.UNAUTHORIZED);
        }
    }
}
