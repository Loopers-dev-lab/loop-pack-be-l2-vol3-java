package com.loopers.domain.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.loopers.infrastructure.user.persistence.UserJpaRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.utils.DatabaseCleanUp;

@SpringBootTest
class UserServiceIntegrationTest {

    @Autowired
    private UserService userService;

    @Autowired
    private UserJpaRepository userJpaRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("회원가입을 할 때,")
    @Nested
    class SignUp {

        @DisplayName("유효한 정보를 입력하면, 회원이 DB에 저장된다.")
        @Test
        void savesUserToDatabase_whenValidInputProvided() {
            // arrange
            String loginId = "user123";
            String password = "Password1!";
            String name = "홍길동";
            String birthDate = "1990-01-01";
            String email = "test@email.com";

            // act
            User user = userService.signUp(loginId, password, name, birthDate, email);

            // assert
            assertAll(
                    () -> assertThat(user.getId()).isNotNull(),
                    () -> assertThat(user.getLoginId()).isEqualTo(new LoginId(loginId)),
                    () -> assertThat(user.getName()).isEqualTo(new UserName(name)),
                    () -> assertThat(user.getBirthDate()).isEqualTo(new BirthDate(birthDate)),
                    () -> assertThat(user.getEmail()).isEqualTo(new Email(email)),
                    () -> assertThat(user.getPassword().getValue()).isNotEqualTo(password)
            );
        }

        @DisplayName("이미 가입된 로그인 ID로 가입하면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenLoginIdAlreadyExists() {
            // arrange
            String loginId = "user123";
            userService.signUp(loginId, "Password1!", "홍길동", "1990-01-01", "test@email.com");

            // act & assert
            assertThatThrownBy(() -> userService.signUp(loginId, "Password2!", "김철수", "1995-05-05", "other@email.com"))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));
        }
    }
}
