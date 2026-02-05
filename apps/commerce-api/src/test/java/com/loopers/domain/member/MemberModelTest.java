package com.loopers.domain.member;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
}
