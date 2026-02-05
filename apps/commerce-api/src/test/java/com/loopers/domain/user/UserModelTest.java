package com.loopers.domain.user;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

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
}
