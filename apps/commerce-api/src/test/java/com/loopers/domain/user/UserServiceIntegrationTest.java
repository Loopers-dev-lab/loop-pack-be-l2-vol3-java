package com.loopers.domain.user;

import com.loopers.infrastructure.user.UserJpaRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
class UserServiceIntegrationTest {

    private static final String VALID_LOGIN_ID = "namjin123";
    private static final String VALID_PASSWORD = "qwer@1234";
    private static final String VALID_NAME = "namjin";
    private static final String VALID_BIRTHDAY = "1994-05-25";
    private static final String VALID_EMAIL = "test@gmail.com";

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

    @DisplayName("회원가입을 할 때, ")
    @Nested
    class Signup {

        @DisplayName("정상적인 정보로 회원가입이 성공한다.")
        @Test
        void signupSucceeds_whenInfoIsValid() {
            // arrange
            SignupCommand command = new SignupCommand(VALID_LOGIN_ID, VALID_PASSWORD, VALID_NAME, VALID_BIRTHDAY, VALID_EMAIL);

            // act
            UserModel result = userService.signup(command);

            // assert
            assertThat(result).isNotNull();
            assertThat(result.getId()).isNotNull();
            assertThat(result.getLoginId()).isEqualTo(command.loginId());

            // DB에 실제로 저장됐는지 확인
            assertThat(userJpaRepository.findById(result.getId())).isPresent();
        }

        @DisplayName("이미 가입된 로그인 ID로 가입하면, 예외가 발생한다.")
        @Test
        void throwsException_whenLoginIdAlreadyExists() {
            // arrange - 먼저 회원 하나 저장
            UserModel existingUser = new UserModel(VALID_LOGIN_ID, "otherPw@123", "기존회원", "1990-01-01", "other@test.com");
            userJpaRepository.save(existingUser);

            // 같은 loginId로 가입 시도
            SignupCommand command = new SignupCommand(VALID_LOGIN_ID, "newPw@1234", "신규회원", "1995-05-05", "new@test.com");

            // act & assert
            CoreException result = assertThrows(CoreException.class, () -> {
                userService.signup(command);
            });

            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}
