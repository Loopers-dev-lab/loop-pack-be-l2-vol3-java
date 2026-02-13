package com.loopers.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.loopers.infrastructure.PasswordEncoder;
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
    private PasswordEncoder passwordEncoder;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    private LoginId validLoginId;
    private Password validPassword;
    private String rawPassword;
    private Name validName;
    private BirthDate validBirthDate;
    private Email validEmail;

    @BeforeEach
    void setUp() {
        validLoginId = new LoginId("testuser123");
        rawPassword = "Test1234!@#";
        validPassword = new Password(rawPassword);
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
                        () -> assertThat(result.getName()).isEqualTo(validName),
                        () -> assertThat(result.getBirthDate()).isEqualTo(validBirthDate),
                        () -> assertThat(result.getEmail()).isEqualTo(validEmail)
                );
            }

            @DisplayName("비밀번호를 암호화하여 저장한다")
            @Test
            void signup_should_encrypt_password() {
                // act
                UserModel result = userService.signup(validLoginId,validPassword,validName,validBirthDate,validEmail);

                // assert
                String savedPassword = result.getPassword().getValue();
                assertAll(
                        () -> assertThat(savedPassword).isNotEqualTo(rawPassword), // 평문과 다름
                        () -> assertThat(savedPassword).startsWith("$2a$"), // BCrypt 포맷
                        () -> assertThat(passwordEncoder.matches(rawPassword, savedPassword)).isTrue() // 평문과 매칭됨
                );
            }

            @DisplayName("DB에 저장된 비밀번호가 암호화되어 있다")
            @Test
            void signup_should_save_encrypted_password_to_database() {
                // act
                UserModel result = userService.signup(validLoginId,validPassword,validName,validBirthDate,validEmail);

                // assert
                UserModel savedUser = userJpaRepository.findById(result.getId()).orElseThrow();
                String savedPassword = savedUser.getPassword().getValue();
                assertAll(
                        () -> assertThat(savedPassword).isNotEqualTo(rawPassword), // 평문과 다름
                        () -> assertThat(savedPassword).startsWith("$2a$"), // BCrypt 포맷
                        () -> assertThat(passwordEncoder.matches(rawPassword, savedPassword)).isTrue() // 평문과 매칭됨
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

    @DisplayName("유저가 비밀번호를 변경할 때")
    @Nested
    class ChangePassword {
        @DisplayName("올바른 현재 비밀번호와 새 비밀번호를 주면 비밀번호가 변경된다")
        @Test
        void changePassword_whenValidPasswords() {
            // arrange
            userService.signup(validLoginId, validPassword, validName, validBirthDate, validEmail);
            Password newPassword = new Password("NewPass123!@");

            // act
            userService.changePassword(validLoginId, validPassword, newPassword);

            // assert
            UserModel updatedUser = userService.getMyInfo(validLoginId);
            String savedPassword = updatedUser.getPassword().getValue();
            assertAll(
                    () -> assertThat(savedPassword).isNotEqualTo(rawPassword), // 이전 평문과 다름
                    () -> assertThat(savedPassword).isNotEqualTo(newPassword.getValue()), // 새 평문과도 다름 (암호화됨)
                    () -> assertThat(passwordEncoder.matches(newPassword.getValue(), savedPassword)).isTrue() // 새 비밀번호와 매칭됨
            );
        }

        @DisplayName("현재 비밀번호가 일치하지 않으면 예외가 발생한다")
        @Test
        void changePassword_whenCurrentPasswordNotMatch() {
            // arrange
            userService.signup(validLoginId, validPassword, validName, validBirthDate, validEmail);
            Password wrongPassword = new Password("Wrong123!@#");
            Password newPassword = new Password("NewPass123!@");

            // act & assert
            assertThatThrownBy(() -> userService.changePassword(validLoginId, wrongPassword, newPassword))
                .isInstanceOf(CoreException.class)
                .hasMessageContaining("현재 비밀번호가 일치하지 않습니다.");
        }

        @DisplayName("새 비밀번호가 현재 비밀번호와 동일하면 예외가 발생한다")
        @Test
        void changePassword_whenNewPasswordSameAsCurrent() {
            // arrange
            userService.signup(validLoginId, validPassword, validName, validBirthDate, validEmail);
            Password samePassword = new Password("Test1234!@#");

            // act & assert
            assertThatThrownBy(() -> userService.changePassword(validLoginId, validPassword, samePassword))
                .isInstanceOf(CoreException.class)
                .hasMessageContaining("현재 사용 중인 비밀번호는 사용할 수 없습니다.");
        }

        @DisplayName("새 비밀번호에 생년월일이 포함되면 예외가 발생한다")
        @Test
        void changePassword_whenNewPasswordContainsBirthDate() {
            // arrange
            userService.signup(validLoginId, validPassword, validName, validBirthDate, validEmail);
            Password newPasswordWithBirthDate = new Password("Pw19900115!");

            // act & assert
            assertThatThrownBy(() -> userService.changePassword(validLoginId, validPassword, newPasswordWithBirthDate))
                .isInstanceOf(CoreException.class)
                .hasMessageContaining("생년월일은 비밀번호 내에 포함될 수 없습니다.");
        }

        @DisplayName("존재하지 않는 사용자의 비밀번호 변경 시 예외가 발생한다")
        @Test
        void changePassword_whenUserNotFound() {
            // arrange
            LoginId invalidLoginId = new LoginId("invalid123");
            Password newPassword = new Password("NewPass123!@");

            // act & assert
            assertThatThrownBy(() -> userService.changePassword(invalidLoginId, validPassword, newPassword))
                .isInstanceOf(CoreException.class)
                .hasFieldOrPropertyWithValue("errorType", ErrorType.NOT_FOUND)
                .hasMessageContaining("사용자를 찾을 수 없습니다.");
        }
    }
}
