package com.loopers.domain.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.loopers.infrastructure.user.UserJpaRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.utils.DatabaseCleanUp;
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
    private PasswordEncoder passwordEncoder;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    private String loginId;
    private String rawPassword;
    private String name;
    private String birthDate;
    private String email;

    @BeforeEach
    void setUp() {
        loginId = "testuser123";
        rawPassword = "Test1234!@#";
        name = "홍길동";
        birthDate = "19900115";
        email = "test@example.com";
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private UserModel doSignup() {
        return userService.signup(loginId, rawPassword, name, birthDate, email);
    }

    @DisplayName("유저가 회원가입할 때")
    @Nested
    class SingUp{
            @DisplayName("로그인 ID, 비밀번호, 이름, 생년월일, 이메일을 주면, 회원가입을 한다.")
            @Test
            void signup_whenAllInfoProvided() {
                // act
                UserModel result = doSignup();

                // assert
                assertAll(
                        () -> assertThat(result).isNotNull(),
                        () -> assertThat(result.getLoginId()).isEqualTo(loginId),
                        () -> assertThat(result.getName()).isEqualTo(name),
                        () -> assertThat(result.getBirthDateString()).isEqualTo(birthDate),
                        () -> assertThat(result.getEmail()).isEqualTo(email)
                );
            }

            @DisplayName("비밀번호를 암호화하여 저장한다")
            @Test
            void signup_should_encrypt_password() {
                // act
                UserModel result = doSignup();

                // assert
                UserModel savedUser = userJpaRepository.findById(result.getId()).orElseThrow();
                String savedPassword = savedUser.getPassword();
                assertAll(
                        () -> assertThat(savedPassword).isNotEqualTo(rawPassword),
                        () -> assertThat(savedPassword).startsWith("$2a$"),
                        () -> assertThat(passwordEncoder.matches(rawPassword, savedPassword)).isTrue()
                );
            }

            @DisplayName("DB에 저장된 비밀번호가 암호화되어 있다")
            @Test
            void signup_should_save_encrypted_password_to_database() {
                // act
                UserModel result = doSignup();

                // assert
                UserModel savedUser = userJpaRepository.findById(result.getId()).orElseThrow();
                String savedPassword = savedUser.getPassword();
                assertAll(
                        () -> assertThat(savedPassword).isNotEqualTo(rawPassword),
                        () -> assertThat(savedPassword).startsWith("$2a$"),
                        () -> assertThat(passwordEncoder.matches(rawPassword, savedPassword)).isTrue()
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
            doSignup();

            // act
            UserModel result = userService.getByLoginId(loginId);

            // assert
            assertAll(
                () -> assertThat(result).isNotNull(),
                () -> assertThat(result.getLoginId()).isEqualTo(loginId),
                () -> assertThat(result.getName()).isEqualTo(name),
                () -> assertThat(result.getBirthDateString()).isEqualTo(birthDate),
                () -> assertThat(result.getEmail()).isEqualTo(email)
            );
        }

        @DisplayName("존재하지 않는 로그인 ID로 조회하면 예외가 발생한다")
        @Test
        void getMyInfo_whenInvalidLoginId() {
            // act & assert
            assertThatThrownBy(() -> userService.getByLoginId("invalid123"))
                .isInstanceOf(CoreException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorType.NOT_FOUND)
                .hasMessageContaining("사용자를 찾을 수 없습니다.");
        }
    }

    @DisplayName("유저가 비밀번호를 변경할 때")
    @Nested
    class ChangePassword {
        @DisplayName("올바른 현재 비밀번호와 새 비밀번호를 주면 비밀번호가 변경된다")
        @Test
        void changePassword_whenValidPasswords() {
            // arrange
            doSignup();
            String newRawPassword = "NewPass123!@";

            // act
            userService.changePassword(loginId, rawPassword, newRawPassword);

            // assert
            UserModel updatedUser = userService.getByLoginId(loginId);
            UserModel savedUser = userJpaRepository.findById(updatedUser.getId()).orElseThrow();
            String savedPassword = savedUser.getPassword();
            assertAll(
                    () -> assertThat(savedPassword).isNotEqualTo(rawPassword),
                    () -> assertThat(savedPassword).isNotEqualTo(newRawPassword),
                    () -> assertThat(passwordEncoder.matches(newRawPassword, savedPassword)).isTrue()
            );
        }

        @DisplayName("현재 비밀번호가 일치하지 않으면 예외가 발생한다")
        @Test
        void changePassword_whenCurrentPasswordNotMatch() {
            // arrange
            doSignup();

            // act & assert
            assertThatThrownBy(() -> userService.changePassword(loginId, "Wrong123!@#", "NewPass123!@"))
                .isInstanceOf(CoreException.class)
                .hasMessageContaining("현재 비밀번호가 일치하지 않습니다.");
        }

        @DisplayName("새 비밀번호가 현재 비밀번호와 동일하면 예외가 발생한다")
        @Test
        void changePassword_whenNewPasswordSameAsCurrent() {
            // arrange
            doSignup();

            // act & assert
            assertThatThrownBy(() -> userService.changePassword(loginId, rawPassword, rawPassword))
                .isInstanceOf(CoreException.class)
                .hasMessageContaining("현재 사용 중인 비밀번호는 사용할 수 없습니다.");
        }

        @DisplayName("새 비밀번호에 생년월일이 포함되면 예외가 발생한다")
        @Test
        void changePassword_whenNewPasswordContainsBirthDate() {
            // arrange
            doSignup();

            // act & assert
            assertThatThrownBy(() -> userService.changePassword(loginId, rawPassword, "Pw19900115!"))
                .isInstanceOf(CoreException.class)
                .hasMessageContaining("생년월일은 비밀번호 내에 포함될 수 없습니다.");
        }

        @DisplayName("존재하지 않는 사용자의 비밀번호 변경 시 예외가 발생한다")
        @Test
        void changePassword_whenUserNotFound() {
            // act & assert
            assertThatThrownBy(() -> userService.changePassword("invalid123", rawPassword, "NewPass123!@"))
                .isInstanceOf(CoreException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorType.NOT_FOUND)
                .hasMessageContaining("사용자를 찾을 수 없습니다.");
        }
    }
}
