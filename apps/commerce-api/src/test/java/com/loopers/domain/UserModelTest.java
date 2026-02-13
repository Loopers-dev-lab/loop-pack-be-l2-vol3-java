package com.loopers.domain;

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

    @DisplayName("유저 모델을 생성할 때, ")
    @Nested
    class Create {

        @DisplayName("모든 필드가 주어지면, 정상적으로 생성된다.")
        @Test
        void createUserModel_whenAllDataProvided() {
            // act
            UserModel user = new UserModel(validLoginId, validPassword, validName, validBirthDate, validEmail);

            // assert
            assertAll(
                    () -> assertThat(user.getLoginId()).isEqualTo(validLoginId),
                    () -> assertThat(user.getPassword()).isEqualTo(validPassword),
                    () -> assertThat(user.getName()).isEqualTo(validName),
                    () -> assertThat(user.getBirthDate()).isEqualTo(validBirthDate),
                    () -> assertThat(user.getEmail()).isEqualTo(validEmail)
            );
        }

        @DisplayName("로그인 ID가 누락되면 예외가 발생한다.")
        @Test
        void createUserModel_whenLoginIdIsNull() {
            assertThatThrownBy(() -> new UserModel(null, validPassword, validName, validBirthDate, validEmail))
                    .isInstanceOf(CoreException.class);
        }

        @DisplayName("비밀번호가 누락되면 예외가 발생한다.")
        @Test
        void createUserModel_whenPasswordIsNull() {
            assertThatThrownBy(() -> new UserModel(validLoginId, null, validName, validBirthDate, validEmail))
                    .isInstanceOf(CoreException.class);
        }

        @DisplayName("이름이 누락되면 예외가 발생한다.")
        @Test
        void createUserModel_whenNameIsNull() {
            assertThatThrownBy(() -> new UserModel(validLoginId, validPassword, null, validBirthDate, validEmail))
                    .isInstanceOf(CoreException.class);
        }

        @DisplayName("생년월일이 누락되면 예외가 발생한다.")
        @Test
        void createUserModel_whenBirthDateIsNull() {
            assertThatThrownBy(() -> new UserModel(validLoginId, validPassword, validName, null, validEmail))
                    .isInstanceOf(CoreException.class);
        }

        @DisplayName("이메일이 누락되면 예외가 발생한다.")
        @Test
        void createUserModel_whenEmailIsNull() {
            assertThatThrownBy(() -> new UserModel(validLoginId, validPassword, validName, validBirthDate, null))
                    .isInstanceOf(CoreException.class);
        }
    }
}
