package com.loopers.domain.user;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserTest {

    @DisplayName("회원가입 성공: 모든 정보가 유효하면 가입 완료")
    @Test
    void createUser() {
        String loginId = "testId123";
        String password = "testpw123";
        String name = "김윤선";
        LocalDate birthDate = LocalDate.parse("1997-10-08");
        String email = "kimyam1008@gmail.com";

        RawPassword rawPassword = new RawPassword(password);
        EncryptedPassword encryptedPassword = new EncryptedPassword("ENCODED_" + password);

        User user = User.create(loginId, rawPassword, encryptedPassword, name, birthDate, email, Gender.FEMALE);

        assertThat(user).isNotNull();
        assertThat(user.getLoginId()).isEqualTo(loginId);
        assertThat(user.getName()).isEqualTo(name);
        assertThat(user.getBirthDate()).isEqualTo(birthDate);
        assertThat(user.getEmail()).isEqualTo(email);
        assertThat(user.getPassword()).isNotBlank();
        assertThat(user.getPassword()).isNotEqualTo(password);
        assertThat(user.getPassword()).isEqualTo("ENCODED_testpw123");
    }

    @DisplayName("회원가입 실패: 이름 형식이 올바르지 않으면 예외 발생")
    @ParameterizedTest
    @ValueSource(strings = {"김@윤선", "test!", "1234"})
    void createUserFailByInvalidName(String invalidName) {
        assertThatThrownBy(() -> createUser("id", "Password1!", invalidName, LocalDate.now(), "a@b.com", Gender.MALE))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("이름 형식이 올바르지 않습니다");
    }

    @DisplayName("회원가입 실패: 비밀번호에 생년월일이 포함되면 예외 발생")
    @Test
    void createUserFailByPasswordContainingBirthDate() {
        LocalDate birthDate = LocalDate.of(1997, 10, 8);
        String invalidPassword = "Pw19971008!";

        assertThatThrownBy(() -> createUser("validid", invalidPassword, "김윤선", birthDate, "valid@email.com", Gender.MALE))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("생년월일은 비밀번호 내에 포함될 수 없습니다");
    }

    @DisplayName("회원가입 실패: 로그인 ID 형식이 유효하지 않으면 예외 발생 (영문/숫자 10자 이내)")
    @ParameterizedTest
    @ValueSource(strings = {"idWithSpecial!", "toolongideeeee", "한글아이디"})
    void createUserFailByInvalidId(String invalidId) {
        assertThatThrownBy(() -> createUser(invalidId, "Password1!", "김윤선", LocalDate.now(), "valid@email.com", Gender.MALE))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("ID 형식이 올바르지 않습니다");
    }

    @DisplayName("회원가입 실패: 이메일 형식이 유효하지 않으면 예외 발생")
    @ParameterizedTest
    @ValueSource(strings = {"invalid-email", "abc@def", "abc.def", "abc@.com"})
    void createUserFailByInvalidEmail(String invalidEmail) {
        assertThatThrownBy(() -> createUser("validid", "Password1!", "김윤선", LocalDate.now(), invalidEmail, Gender.MALE))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("이메일 형식이 올바르지 않습니다");
    }

    @DisplayName("회원가입 실패: 비밀번호 형식이 유효하지 않으면 예외 발생 (8~16자, 영문/숫자/특수문자만 사용 가능)")
    @ParameterizedTest
    @ValueSource(strings = {
            "short",                     // 8자 미만
            "verylongpassword123!extra", // 16자 초과
            "한글포함password1",           // 한글 포함
            "emoji😊pass1",               // 이모지 포함
            "공백 포함pw1"                 // 공백 포함
    })
    void createUserFailByInvalidPasswordPattern(String invalidPassword) {
        assertThatThrownBy(() -> new RawPassword(invalidPassword))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("비밀번호 형식이 올바르지 않습니다");
    }

    @DisplayName("회원가입 실패: 생년월일이 미래 날짜이면 예외 발생")
    @Test
    void createUserFailByFutureBirthDate() {
        LocalDate futureBirthDate = LocalDate.now().plusDays(1);

        assertThatThrownBy(() -> createUser("validid", "Password1!", "김윤선", futureBirthDate, "valid@email.com", Gender.MALE))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("생년월일 형식이 올바르지 않습니다");
    }

    @DisplayName("회원가입 실패: 생년월일이 1900년 이전이면 예외 발생")
    @Test
    void createUserFailByTooOldBirthDate() {
        LocalDate tooOldBirthDate = LocalDate.of(1899, 12, 31);

        assertThatThrownBy(() -> createUser("validid", "Password1!", "김윤선", tooOldBirthDate, "valid@email.com", Gender.MALE))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("생년월일 형식이 올바르지 않습니다");
    }

    @DisplayName("회원가입 실패: 성별이 null이면 예외 발생")
    @Test
    void createUserFailByNullGender() {
        assertThatThrownBy(() -> createUser("validid", "Password1!", "김윤선", LocalDate.now(), "valid@email.com", null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("성별은 필수입니다");
    }

    private User createUser(
        String loginId,
        String rawPassword,
        String name,
        LocalDate birthDate,
        String email,
        Gender gender
    ) {
        RawPassword password = new RawPassword(rawPassword);
        EncryptedPassword encryptedPassword = new EncryptedPassword("ENCODED_" + rawPassword);
        return User.create(loginId, password, encryptedPassword, name, birthDate, email, gender);
    }
}
