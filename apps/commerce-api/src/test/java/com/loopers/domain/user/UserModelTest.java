package com.loopers.domain.user;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UserModelTest {

    @DisplayName("사용자를 생성할 때, ")
    @Nested
    class Create {

        @DisplayName("올바른 입력값이 주어지면, 포인트가 0으로 초기화된다.")
        @Test
        void create_withValidInputs_shouldInitializePointsToZero() {
            // given
            String userId = "testuser01";
            Email email = new Email("test@example.com");
            BirthDate birthDate = new BirthDate("1990-01-15");
            Password password = Password.of("SecurePass1!", birthDate);
            Gender gender = Gender.MALE;

            // when
            UserModel user = UserModel.create(userId, email, birthDate, password, gender);

            // then
            assertAll(
                () -> assertThat(user.getUserId()).isEqualTo(userId),
                () -> assertThat(user.getPoints()).isEqualTo(0L),
                () -> assertThat(user.getGender()).isEqualTo(gender)
            );
        }

        @DisplayName("Email VO의 value 값이 추출되어 저장된다.")
        @Test
        void create_shouldStoreExtractedEmailValue() {
            // given
            String userId = "testuser02";
            String emailValue = "user@example.com";
            Email email = new Email(emailValue);
            BirthDate birthDate = new BirthDate("1995-03-20");
            Password password = Password.of("MyPass123!", birthDate);
            Gender gender = Gender.FEMALE;

            // when
            UserModel user = UserModel.create(userId, email, birthDate, password, gender);

            // then
            assertThat(user.getEmail()).isEqualTo(emailValue);
        }

        @DisplayName("BirthDate VO의 value 값이 추출되어 저장된다.")
        @Test
        void create_shouldStoreExtractedBirthDateValue() {
            // given
            String userId = "testuser03";
            String birthDateValue = "1988-12-25";
            Email email = new Email("test3@example.com");
            BirthDate birthDate = new BirthDate(birthDateValue);
            Password password = Password.of("Pass1234!", birthDate);
            Gender gender = Gender.MALE;

            // when
            UserModel user = UserModel.create(userId, email, birthDate, password, gender);

            // then
            assertThat(user.getBirthDate()).isEqualTo(birthDateValue);
        }

        @DisplayName("Password가 암호화되어 저장된다.")
        @Test
        void create_shouldStoreEncryptedPassword() {
            // given
            String userId = "testuser04";
            String rawPassword = "RawPass123!";
            Email email = new Email("test4@example.com");
            BirthDate birthDate = new BirthDate("1992-06-10");
            Password password = Password.of(rawPassword, birthDate);
            Gender gender = Gender.FEMALE;

            // when
            UserModel user = UserModel.create(userId, email, birthDate, password, gender);

            // then
            assertThat(user.getEncryptedPassword()).isNotEqualTo(rawPassword);
            assertThat(user.getEncryptedPassword()).isNotBlank();
        }
    }

    @DisplayName("비밀번호를 변경할 때, ")
    @Nested
    class UpdatePassword {

        @DisplayName("현재 비밀번호가 일치하지 않으면, IllegalArgumentException이 발생한다.")
        @Test
        void updatePassword_withIncorrectCurrentPassword_shouldFail() {
            // given
            String userId = "testuser01";
            Email email = new Email("test@example.com");
            BirthDate birthDate = new BirthDate("1990-01-15");
            Password currentPassword = Password.of("OldPass123!", birthDate);
            Gender gender = Gender.MALE;

            UserModel user = UserModel.create(userId, email, birthDate, currentPassword, gender);

            String wrongCurrentPassword = "WrongPass!";
            Password newPassword = Password.of("NewPass456!", birthDate);

            // when & then
            assertThrows(IllegalArgumentException.class, () -> {
                user.updatePassword(wrongCurrentPassword, newPassword);
            });
        }

        @DisplayName("새 비밀번호가 현재 비밀번호와 동일하면, IllegalArgumentException이 발생한다.")
        @Test
        void updatePassword_withSamePassword_shouldFail() {
            // given
            String userId = "testuser02";
            Email email = new Email("test2@example.com");
            BirthDate birthDate = new BirthDate("1990-01-15");
            String samePassword = "SamePass123!";
            Password currentPassword = Password.of(samePassword, birthDate);
            Gender gender = Gender.MALE;

            UserModel user = UserModel.create(userId, email, birthDate, currentPassword, gender);

            Password newPassword = Password.of(samePassword, birthDate);

            // when & then
            assertThrows(IllegalArgumentException.class, () -> {
                user.updatePassword(samePassword, newPassword);
            });
        }

        @DisplayName("올바른 현재 비밀번호와 새 비밀번호가 주어지면, 비밀번호가 변경된다.")
        @Test
        void updatePassword_withValidPasswords_shouldSuccess() {
            // given
            String userId = "testuser03";
            Email email = new Email("test3@example.com");
            BirthDate birthDate = new BirthDate("1990-01-15");
            String currentPasswordValue = "OldPass123!";
            Password currentPassword = Password.of(currentPasswordValue, birthDate);
            Gender gender = Gender.MALE;

            UserModel user = UserModel.create(userId, email, birthDate, currentPassword, gender);
            String oldEncryptedPassword = user.getEncryptedPassword();

            Password newPassword = Password.of("NewPass456!", birthDate);

            // when
            user.updatePassword(currentPasswordValue, newPassword);

            // then
            assertThat(user.getEncryptedPassword()).isNotEqualTo(oldEncryptedPassword);
            assertThat(Password.matches("NewPass456!", user.getEncryptedPassword())).isTrue();
        }
    }
}
