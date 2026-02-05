package com.loopers.domain.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class MemberModelTest {

  @DisplayName("회원 모델을 생성할 때, ")
  @Nested
  class Create {

    @DisplayName("(성공케이스) 필수 정보가 모두 주어지면, 정상적으로 생성된다.")
    @Test
    void createsMemberModel_whenAllFieldsAreProvided() {
      // arrange
      String loginId = "testuser";
      String rawPassword = "Test1234!";
      String name = "홍길동";
      String birthDate = "19900101";
      String email = "test@example.com";

      // act
      MemberModel member = new MemberModel(loginId, rawPassword, name, birthDate, email);

      // assert
      assertThat(member.getLoginId()).isEqualTo(loginId);
      assertThat(member.getPassword()).isEqualTo(rawPassword);
      assertThat(member.getName()).isEqualTo(name);
      assertThat(member.getBirthDate()).isEqualTo(birthDate);
      assertThat(member.getEmail()).isEqualTo(email);
      // 비밀번호는 암호화되어 저장되므로 원본과 다를 수 있음 - 나중에 검증 방식 결정
    }

    @DisplayName("아이디로 회원 모델을 생성할 때, 영문과 숫자가 아닌 문자가 포함되면 예외가 발생한다.")
    @Test
    void throwsBadRequestException_whenLoginIdContainsInvalidChars() {
      // arrange
      String loginId = "testuser!@#";

      // act
      CoreException result = assertThrows(CoreException.class, () -> {
        new MemberModel(loginId);
      });
      // assert
      assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
    }


    @DisplayName("(실패케이스) 비밀번호가 7자일때, 예외발생.")
    @Test
    void throwsBadRequestException_whenPwIsOutOfRange() {
      // arrange
      String loginId = "testuser";
      String password = "Test12!";
      String name = "홍길동";
      String birthDate = "19900101";
      String email = "test@example.com";

      // act
      CoreException result = assertThrows(CoreException.class, () -> {
        new MemberModel(loginId, password, name, birthDate, email);
      });

      // assert
      assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
    }


    @DisplayName("비밀번호가 17자일 때 → 예외 발생")
    @Test
    void throwsBadRequestException_whenPwIsOutOfRange2() {
      // arrange
      String loginId = "testuser";
      String password = "Test123456789012!";
      String name = "홍길동";
      String birthDate = "19900101";
      String email = "test@example.com";

      // act
      CoreException result = assertThrows(CoreException.class, () -> {
        new MemberModel(loginId, password, name, birthDate, email);
      });

      // assert
      assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
    }

    @DisplayName("비밀번호에 한글이 있을 때 → 예외 발생")
    @Test
    void throwsBadRequestException_whenPwIsKorean() {
      // arrange
      String loginId = "testuser";
      String password = "Test홍길동890123!";
      String name = "홍길동";
      String birthDate = "19900101";
      String email = "test@example.com";

      // act
      CoreException result = assertThrows(CoreException.class, () -> {
        new MemberModel(loginId, password, name, birthDate, email);
      });

      // assert
      assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
    }

    @DisplayName("비밀번호에 생년월일이 포함될 때 → 예외 발생")
    @Test
    void throwsBadRequestException_whenPwContainsBirthDate() {
      // arrange
      String loginId = "testuser";
      String password = "Test19900101!";
      String name = "홍길동";
      String birthDate = "19900101";
      String email = "test@example.com";

      // act
      CoreException result = assertThrows(CoreException.class, () -> {
        new MemberModel(loginId, password, name, birthDate, email);
      });

      // assert
      assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
    }
  }

  @DisplayName("회원정보조회할 때,")
  @Nested
  class GetMemberInfo {

    @DisplayName("이름 마지막 글자를 마스킹한다")
    @Test
    void mask_last_character() {
      //arrange
      String loginId = "testuser";
      String rawPassword = "Test1234!";
      String name = "홍길동";
      String birthDate = "19900101";
      String email = "test@example.com";

      // act
      MemberModel member = new MemberModel(loginId, rawPassword, name, birthDate, email);

      assertThat(member.getMaskedName()).isEqualTo("홍길*");
    }

    @DisplayName("이름 마지막 글자를 마스킹한다")
    @Test
    void single_character_name_is_fully_masked() {
      //arrange
      String loginId = "testuser";
      String rawPassword = "Test1234!";
      String name = "홍";
      String birthDate = "19900101";
      String email = "test@example.com";

      // act
      MemberModel member = new MemberModel(loginId, rawPassword, name, birthDate, email);

      assertThat(member.getMaskedName()).isEqualTo("*");
    }

  }

  @DisplayName("비밀번호 수정 할 때,")
  @Nested
  class ChangePassword {

    @DisplayName("새 비밀번호가 규칙을 만족하면, 비밀번호가 변경된다.")
    @Test
    void changesPassword_whenOldPasswordMatchesAndNewPasswordIsValid() {
      // arrange
      String loginId = "testuser";
      String prevPassword = "Test1234!";
      String name = "홍길동";
      String birthDate = "19900101";
      String email = "test@test.co.kr";

      // act
      MemberModel member = new MemberModel(loginId, prevPassword, name, birthDate, email);
      String newPassword = "Newpass123!";
      member.changePassword(newPassword, birthDate);

      // assert
      assertThat(member.getPassword()).isEqualTo(newPassword);
    }


    @DisplayName("새 비밀번호에 생년월일이 포함되면, 예외가 발생한다.")
    @Test
    void throwsException_whenNewPasswordContainsBirthDate() {
      // arrange
      String loginId = "testuser";
      String prevPassword = "Test1234!";
      String name = "홍길동";
      String birthDate = "19900101";
      String email = "test@test.co.kr";

      String newPassword = "Test19900101!";

      // act
      MemberModel member = new MemberModel(loginId, prevPassword, name, birthDate, email);

      // act
      CoreException result = assertThrows(CoreException.class, () -> {
        member.changePassword(newPassword, birthDate);
      });

      // assert - ErrorType.BAD_REQUEST
      assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);

    }
  }
}
