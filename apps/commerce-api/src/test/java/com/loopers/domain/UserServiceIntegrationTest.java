package com.loopers.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.loopers.infrastructure.UserJpaRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.utils.DatabaseCleanUp;
import java.time.LocalDate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class UserServiceIntegrationTest {
    @Autowired
    private UserService userService;

    @Autowired
    private UserJpaRepository userJpaRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    private LoginId validLoginId;
    private Password validPassword;
    private Name validName;
    private BirthDate validBirthDate;
    private Email validEmail;

    @BeforeEach
    void setUp() {
        validLoginId = new LoginId("testuser123");
        validPassword = new Password("Test1234!@#");
        validName = new Name("홍길동");
        validBirthDate = new BirthDate(LocalDate.of(1990, 1, 15));
        validEmail = new Email("test@example.com");
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("유저가 회원가입할 때")
    @Nested
    class SingUp{
            @DisplayName("로그인 ID, 비밀번호, 이름, 생년월일, 이메일을 주면, 회원가입을 한다.")
            @Test
            void signup_whenAllInfoProvided() {
                // act
                UserModel result = userService.signup(validLoginId,validPassword,validName,validBirthDate,validEmail);

                // assert
                assertAll(
                        () -> assertThat(result).isNotNull(),
                        () -> assertThat(result.getLoginId()).isEqualTo(validLoginId),
                        () -> assertThat(result.getPassword()).isEqualTo(validPassword),
                        () -> assertThat(result.getName()).isEqualTo(validName),
                        () -> assertThat(result.getBirthDate()).isEqualTo(validBirthDate),
                        () -> assertThat(result.getEmail()).isEqualTo(validEmail)
                );
            }
    }

    @DisplayName("유저가 내 정보를 조회할 때")
    @Nested
    class GetMyInfo {
        @DisplayName("로그인 ID로 내 정보를 조회한다")
        @Test
        void getMyInfo_whenValidLoginId() {
            // arrange
            userService.signup(validLoginId, validPassword, validName, validBirthDate, validEmail);

            // act
            UserModel result = userService.getMyInfo(validLoginId);

            // assert
            assertAll(
                () -> assertThat(result).isNotNull(),
                () -> assertThat(result.getLoginId()).isEqualTo(validLoginId),
                () -> assertThat(result.getName()).isEqualTo(validName),
                () -> assertThat(result.getBirthDate()).isEqualTo(validBirthDate),
                () -> assertThat(result.getEmail()).isEqualTo(validEmail)
            );
        }

        @DisplayName("존재하지 않는 로그인 ID로 조회하면 예외가 발생한다")
        @Test
        void getMyInfo_whenInvalidLoginId() {
            // arrange
            LoginId invalidLoginId = new LoginId("invalid123");

            // act & assert
            assertThatThrownBy(() -> userService.getMyInfo(invalidLoginId))
                .isInstanceOf(CoreException.class)
                .hasFieldOrPropertyWithValue("errorType", ErrorType.NOT_FOUND)
                .hasMessageContaining("사용자를 찾을 수 없습니다.");
        }
    }
}
