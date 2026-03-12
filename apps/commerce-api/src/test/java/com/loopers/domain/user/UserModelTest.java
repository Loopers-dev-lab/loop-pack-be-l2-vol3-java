package com.loopers.domain.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.loopers.support.error.CoreException;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class UserModelTest {

    private String validLoginId;
    private String validEncryptedPassword;
    private String validName;
    private LocalDate validBirthDate;
    private String validEmail;

    @BeforeEach
    void setUp() {
        validLoginId = "testuser123";
        validEncryptedPassword = "ENCODED_Test1234!@#";
        validName = "홍길동";
        validBirthDate = LocalDate.of(1990, 1, 15);
        validEmail = "test@example.com";
    }

    @DisplayName("유저 모델을 생성할 때, ")
    @Nested
    class Create {

        @DisplayName("모든 필드가 주어지면, 정상적으로 생성된다.")
        @Test
        void createUserModel_whenAllDataProvided() {
            // act
            UserModel user = UserModel.create(validLoginId, validEncryptedPassword, validName, validBirthDate, validEmail);

            // assert
            assertAll(
                    () -> assertThat(user.getLoginId()).isEqualTo(validLoginId),
                    () -> assertThat(user.getPassword()).isEqualTo(validEncryptedPassword),
                    () -> assertThat(user.getName()).isEqualTo(validName),
                    () -> assertThat(user.getBirthDate()).isEqualTo(validBirthDate),
                    () -> assertThat(user.getEmail()).isEqualTo(validEmail)
            );
        }

        @DisplayName("로그인 ID가 누락되면 예외가 발생한다.")
        @Test
        void createUserModel_whenLoginIdIsNull() {
            assertThatThrownBy(() -> UserModel.create(null, validEncryptedPassword, validName, validBirthDate, validEmail))
                    .isInstanceOf(CoreException.class);
        }

        @DisplayName("이름이 누락되면 예외가 발생한다.")
        @Test
        void createUserModel_whenNameIsNull() {
            assertThatThrownBy(() -> UserModel.create(validLoginId, validEncryptedPassword, null, validBirthDate, validEmail))
                    .isInstanceOf(CoreException.class);
        }

        @DisplayName("생년월일이 누락되면 예외가 발생한다.")
        @Test
        void createUserModel_whenBirthDateIsNull() {
            assertThatThrownBy(() -> UserModel.create(validLoginId, validEncryptedPassword, validName, null, validEmail))
                    .isInstanceOf(CoreException.class);
        }

        @DisplayName("이메일이 누락되면 예외가 발생한다.")
        @Test
        void createUserModel_whenEmailIsNull() {
            assertThatThrownBy(() -> UserModel.create(validLoginId, validEncryptedPassword, validName, validBirthDate, null))
                    .isInstanceOf(CoreException.class);
        }
    }

    @DisplayName("비밀번호를 변경할 때, ")
    @Nested
    class ChangePassword {

        @DisplayName("새 암호화된 비밀번호가 주어지면 비밀번호가 변경된다.")
        @Test
        void changePassword_success() {
            // arrange
            UserModel user = UserModel.create(validLoginId, validEncryptedPassword, validName, validBirthDate, validEmail);

            // act
            user.changePassword("ENCODED_NewPass123!@");

            // assert
            assertThat(user.getPassword()).isEqualTo("ENCODED_NewPass123!@");
        }
    }

    @DisplayName("포인트를 증가할 때, ")
    @Nested
    class AddPoint {

        @DisplayName("양수 금액이 주어지면 포인트가 증가한다.")
        @Test
        void addPoint_success() {
            // arrange
            UserModel user = UserModel.create(validLoginId, validEncryptedPassword, validName, validBirthDate, validEmail);

            // act
            user.addPoint(1000L);

            // assert
            assertThat(user.getPoint()).isEqualTo(1000L);
        }

        @DisplayName("0 이하의 금액이면 예외가 발생한다.")
        @Test
        void addPoint_zeroOrNegative_throwsException() {
            // arrange
            UserModel user = UserModel.create(validLoginId, validEncryptedPassword, validName, validBirthDate, validEmail);

            // act & assert
            assertThatThrownBy(() -> user.addPoint(0L))
                    .isInstanceOf(CoreException.class);
        }
    }

    @DisplayName("포인트를 차감할 때, ")
    @Nested
    class DeductPoint {

        @DisplayName("보유 포인트 이하의 금액이면 차감된다.")
        @Test
        void deductPoint_success() {
            // arrange
            UserModel user = UserModel.create(validLoginId, validEncryptedPassword, validName, validBirthDate, validEmail);
            user.addPoint(5000L);

            // act
            user.deductPoint(3000L);

            // assert
            assertThat(user.getPoint()).isEqualTo(2000L);
        }

        @DisplayName("보유 포인트보다 큰 금액이면 예외가 발생한다.")
        @Test
        void deductPoint_insufficientPoint_throwsException() {
            // arrange
            UserModel user = UserModel.create(validLoginId, validEncryptedPassword, validName, validBirthDate, validEmail);
            user.addPoint(1000L);

            // act & assert
            assertThatThrownBy(() -> user.deductPoint(2000L))
                    .isInstanceOf(CoreException.class);
        }

        @DisplayName("0 이하의 금액이면 예외가 발생한다.")
        @Test
        void deductPoint_zeroOrNegative_throwsException() {
            // arrange
            UserModel user = UserModel.create(validLoginId, validEncryptedPassword, validName, validBirthDate, validEmail);

            // act & assert
            assertThatThrownBy(() -> user.deductPoint(0L))
                    .isInstanceOf(CoreException.class);
        }
    }
}
