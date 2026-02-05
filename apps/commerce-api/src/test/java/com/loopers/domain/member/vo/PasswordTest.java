package com.loopers.domain.member.vo;

import com.loopers.domain.member.PasswordEncoder;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PasswordTest {

    @DisplayName("비밀번호 정책(8~16자, 영문/숫자/특수문자 포함)을 준수하면 생성에 성공한다.")
    @Test
    void create_success() {
        // given
        String pw = "PassWord123!";
        BirthDate birthDate = new BirthDate("1997-01-01");

        // when
        Password password = Password.of(pw, birthDate);

        // then
        assertThat(password).isNotNull();
    }

    @DisplayName("비밀번호 길이가 8자 미만이거나, 16자 초과면 예외가 발생한다.")
    @ParameterizedTest
    @ValueSource(strings = {"Short1!", "TooooooooooooLongPassword123!"})
    void create_fail_length(String invalidPw) {
        // given
        BirthDate birthDate = new BirthDate("1997-01-01");

        // when & then
        assertThatThrownBy(() -> Password.of(invalidPw, birthDate))
                .isInstanceOf(CoreException.class)
                .extracting("errorType").isEqualTo(ErrorType.BAD_REQUEST);
    }

    @DisplayName("비밀번호에 영문, 숫자, 특수문자가 모두 포함되지 않으면 예외가 발생한다.")
    @ParameterizedTest
    @ValueSource(strings = {"Password123", "Password!@#", "12345678!@#"})
    void create_fail_complexity(String invalidPw) {
        // given
        BirthDate birthDate = new BirthDate("1997-01-01");

        // when & then
        assertThatThrownBy(() -> Password.of(invalidPw, birthDate))
                .isInstanceOf(CoreException.class)
                .hasMessageContaining("영문, 숫자, 특수문자");
    }

    @DisplayName("비밀번호에 생년월일 패턴(YYYY, YY, MMDD)이 포함되면 예외가 발생한다.")
    @ParameterizedTest
    @CsvSource({
            "1997-12-31, Pass1997!@#",
            "1997-12-31, Pass97!@#abc",
            "1997-12-31, Pass1231!@#",
            "1997-12-31, Pass971231!@#"
    })
    void create_fail_contains_birth_pattern(String birth, String invalidPw) {
        // given
        BirthDate birthDate = new BirthDate(birth);

        // when & then
        assertThatThrownBy(() -> Password.of(invalidPw, birthDate))
                .isInstanceOf(CoreException.class)
                .hasMessageContaining("생년월일");
    }

    @DisplayName("이미 암호화된 비밀번호로 Password 객체를 생성할 수 있다.")
    @Test
    void create_with_encoded_value() {
        // given
        String encodedValue = "$2a$10$someEncodedPasswordValue";

        // when
        Password password = Password.ofEncoded(encodedValue);

        // then
        assertThat(password.getValue()).isEqualTo(encodedValue);
    }

    @DisplayName("비밀번호 변경 테스트")
    @Nested
    class ChangePasswordTest {

        private final PasswordEncoder fakeEncoder = new PasswordEncoder() {
            @Override
            public String encode(String rawPassword) {
                return "encoded:" + rawPassword;
            }

            @Override
            public boolean matches(String rawPassword, String encodedPassword) {
                return encodedPassword.equals("encoded:" + rawPassword);
            }
        };

        @DisplayName("현재 비밀번호가 일치하고 새 비밀번호가 유효하면 변경에 성공한다.")
        @Test
        void change_success() {
            // given
            String currentRaw = "OldPass123!";
            String newRaw = "NewPass456!";
            BirthDate birthDate = new BirthDate("1997-01-01");
            Password password = Password.ofEncoded(fakeEncoder.encode(currentRaw));

            // when
            Password changed = password.change(currentRaw, newRaw, birthDate, fakeEncoder);

            // then
            assertThat(changed.matches(newRaw, fakeEncoder)).isTrue();
        }

        @DisplayName("현재 비밀번호가 일치하지 않으면 예외가 발생한다.")
        @Test
        void change_fail_wrong_current_password() {
            // given
            String currentRaw = "OldPass123!";
            String wrongCurrent = "WrongPass1!";
            String newRaw = "NewPass456!";
            BirthDate birthDate = new BirthDate("1997-01-01");
            Password password = Password.ofEncoded(fakeEncoder.encode(currentRaw));

            // when & then
            assertThatThrownBy(() -> password.change(wrongCurrent, newRaw, birthDate, fakeEncoder))
                    .isInstanceOf(CoreException.class)
                    .hasMessageContaining("현재 비밀번호가 일치하지 않습니다");
        }

        @DisplayName("새 비밀번호가 기존 비밀번호와 동일하면 예외가 발생한다.")
        @Test
        void change_fail_same_password() {
            // given
            String currentRaw = "OldPass123!";
            String newRaw = "OldPass123!";
            BirthDate birthDate = new BirthDate("1997-01-01");
            Password password = Password.ofEncoded(fakeEncoder.encode(currentRaw));

            // when & then
            assertThatThrownBy(() -> password.change(currentRaw, newRaw, birthDate, fakeEncoder))
                    .isInstanceOf(CoreException.class)
                    .hasMessageContaining("기존 비밀번호와 동일한 비밀번호는 사용할 수 없습니다");
        }

        @DisplayName("새 비밀번호가 정책을 위반하면 예외가 발생한다.")
        @Test
        void change_fail_invalid_new_password() {
            // given
            String currentRaw = "OldPass123!";
            String newRaw = "short1!";
            BirthDate birthDate = new BirthDate("1997-01-01");
            Password password = Password.ofEncoded(fakeEncoder.encode(currentRaw));

            // when & then
            assertThatThrownBy(() -> password.change(currentRaw, newRaw, birthDate, fakeEncoder))
                    .isInstanceOf(CoreException.class)
                    .hasMessageContaining("8~16자");
        }
    }
}
