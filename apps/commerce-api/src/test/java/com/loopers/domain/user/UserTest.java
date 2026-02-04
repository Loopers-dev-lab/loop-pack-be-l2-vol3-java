package com.loopers.domain.user;

import com.loopers.support.security.PasswordEncryptor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;
// 검증 라이브러리
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserTest {

    // 테스트용 가짜 PasswordEncryptor 구현체
    private final PasswordEncryptor fakeEncryptor = new PasswordEncryptor() {
        @Override
        public String encode(String rawPassword) {
            return "ENCODED_" + rawPassword;
        }

        @Override
        public boolean matches(String rawPassword, String encryptedPassword) {
            return encryptedPassword.equals("ENCODED_" + rawPassword);
        }
    };

    // @DisplayName 은 편하게 해당 테스트가 무엇을 검증하는지에 대한 한 줄 요약으로 적으면 된다.
    @DisplayName("회원가입 성공: 모든 정보가 유효하면 가입 완료")
    @Test
    void createUser() {
        // given (준비)
        String loginId = "testId123";
        String password = "testpw123";
        String name = "김윤선";
        LocalDate birthDate = LocalDate.parse("1997-10-08");
        String email = "kimyam1008@gmail.com";

        // when (실행)
        // 그렇다면 실제 register 메서드가 있다고 치고?(상상?)
        User user = User.register(loginId, password, name, birthDate, email, fakeEncryptor);

        // then (검증)
        // 첫번째 검증 user 객체가 null 이 아니여야 함
        assertThat(user).isNotNull();
        // 두번째 검증 user 의 ID가 내가 입력한 'id' 가 같아야함
        assertThat(user.getLoginId()).isEqualTo(loginId);
        // 세번째 검증 user 의 이름이 내가 입력한 'name' 과 같아야함
        assertThat(user.getName()).isEqualTo(name);
        // 네번째 검증 user 의 생년월일이 내가 입력한 'birthDate' 와 같아야함
        assertThat(user.getBirthDate()).isEqualTo(birthDate);
        // 다섯번째 검증 user 의 이메일이 내가 입력한 'email' 과 같아야함
        assertThat(user.getEmail()).isEqualTo(email);

        // 비밀번호는 암호화되어 저장되므로 원본과 다르고, 암호화된 형태인지 검증
        assertThat(user.getPassword()).isNotBlank();
        assertThat(user.getPassword()).isNotEqualTo(password); // 평문이 아님
        assertThat(user.getPassword()).isEqualTo("ENCODED_testpw123"); // 암호화됨

    }

    @DisplayName("회원가입 실패: 이름 형식이 올바르지 않으면 예외 발생")
    @ParameterizedTest
    @ValueSource(strings = {"김@윤선", "test!", "1234"})
    void createUserFailByInvalidName(String invalidName) {
        assertThatThrownBy(() -> User.register("id", "Password1!", invalidName, LocalDate.now(), "a@b.com", fakeEncryptor))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("이름 형식이 올바르지 않습니다");
    }

    @DisplayName("회원가입 실패: 비밀번호에 생년월일이 포함되면 예외 발생")
    @Test
    void createUserFailByPasswordContainingBirthDate() {
        // given
        LocalDate birthDate = LocalDate.of(1997, 10, 8);
        String invalidPassword = "Password19971008!"; // 생년월일 포함

        // when & then
        assertThatThrownBy(() -> User.register("validid", invalidPassword, "김윤선", birthDate, "valid@email.com", fakeEncryptor))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("생년월일은 비밀번호 내에 포함될 수 없습니다");
    }

    @DisplayName("회원가입 실패: 로그인 ID 형식이 유효하지 않으면 예외 발생 (영문/숫자 10자 이내)")
    @ParameterizedTest
    @ValueSource(strings = {"idWithSpecial!", "toolongideeeee", "한글아이디"}) // 특수문자, 길이초과, 한글
    void createUserFailByInvalidId(String invalidId) {
        assertThatThrownBy(() -> User.register(invalidId, "Password1!", "김윤선", LocalDate.now(), "valid@email.com", fakeEncryptor))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ID 형식이 올바르지 않습니다");
    }

    @DisplayName("회원가입 실패: 이메일 형식이 유효하지 않으면 예외 발생")
    @ParameterizedTest
    @ValueSource(strings = {"invalid-email", "abc@def", "abc.def", "abc@.com"})
    void createUserFailByInvalidEmail(String invalidEmail) {
        assertThatThrownBy(() -> User.register("validid", "Password1!", "김윤선", LocalDate.now(), invalidEmail, fakeEncryptor))
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
        assertThatThrownBy(() -> User.register("validid", invalidPassword, "김윤선", LocalDate.now(), "valid@email.com", fakeEncryptor))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("비밀번호 형식이 올바르지 않습니다");
    }

    @DisplayName("회원가입 실패: 생년월일이 미래 날짜이면 예외 발생")
    @Test
    void createUserFailByFutureBirthDate() {
        LocalDate futureBirthDate = LocalDate.now().plusDays(1);

        assertThatThrownBy(() -> User.register("validid", "Password1!", "김윤선", futureBirthDate, "valid@email.com", fakeEncryptor))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("생년월일 형식이 올바르지 않습니다");
    }

    @DisplayName("회원가입 실패: 생년월일이 1900년 이전이면 예외 발생")
    @Test
    void createUserFailByTooOldBirthDate() {
        LocalDate tooOldBirthDate = LocalDate.of(1899, 12, 31);

        assertThatThrownBy(() -> User.register("validid", "Password1!", "김윤선", tooOldBirthDate, "valid@email.com", fakeEncryptor))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("생년월일 형식이 올바르지 않습니다");
    }

}

