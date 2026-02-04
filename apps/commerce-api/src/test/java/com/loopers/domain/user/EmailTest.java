package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.UserErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Email Value Object 단위 테스트
 *
 * 검증 규칙:
 * - RFC 5322 표준 이메일 형식
 * - 최대 255자
 * - 한글 불허
 * - 공백 불허
 * - 연속 점 불허 (로컬 파트)
 */
public class EmailTest {

    @DisplayName("이메일을 생성할 때,")
    @Nested
    class Create {

        // ========== 정상 케이스 ==========

        @DisplayName("유효한 이메일이면, 정상적으로 생성된다.")
        @Test
        void createsEmail_whenValid() {
            // arrange
            String value = "nahyeon@example.com";

            // act
            Email email = new Email(value);

            // assert
            assertThat(email.getValue()).isEqualTo(value);
        }

        @DisplayName("서브도메인이 있으면, 정상적으로 생성된다.")
        @Test
        void createsEmail_whenSubdomain() {
            // arrange
            String value = "nahyeon@mail.example.com";

            // act
            Email email = new Email(value);

            // assert
            assertThat(email.getValue()).isEqualTo(value);
        }

        @DisplayName("+ 기호가 포함되면, 정상적으로 생성된다.")
        @Test
        void createsEmail_whenPlusSign() {
            // arrange
            String value = "nahyeon+tag@example.com";

            // act
            Email email = new Email(value);

            // assert
            assertThat(email.getValue()).isEqualTo(value);
        }

        // ========== 엣지 케이스 ==========

        @DisplayName("null이면, 예외가 발생한다.")
        @Test
        void throwsException_whenNull() {
            // act & assert
            CoreException exception = assertThrows(CoreException.class, () -> {
                new Email(null);
            });

            assertThat(exception.getErrorType()).isEqualTo(UserErrorType.INVALID_EMAIL);
        }

        @DisplayName("빈 문자열이면, 예외가 발생한다.")
        @Test
        void throwsException_whenEmpty() {
            // act & assert
            CoreException exception = assertThrows(CoreException.class, () -> {
                new Email("");
            });

            assertThat(exception.getErrorType()).isEqualTo(UserErrorType.INVALID_EMAIL);
        }

        @DisplayName("@가 없으면, 예외가 발생한다.")
        @Test
        void throwsException_whenNoAtSign() {
            // act & assert
            CoreException exception = assertThrows(CoreException.class, () -> {
                new Email("nahyeonexample.com");
            });

            assertThat(exception.getErrorType()).isEqualTo(UserErrorType.INVALID_EMAIL);
        }

        @DisplayName("도메인이 없으면, 예외가 발생한다.")
        @Test
        void throwsException_whenNoDomain() {
            // act & assert
            CoreException exception = assertThrows(CoreException.class, () -> {
                new Email("nahyeon@");
            });

            assertThat(exception.getErrorType()).isEqualTo(UserErrorType.INVALID_EMAIL);
        }

        @DisplayName("로컬 파트가 없으면, 예외가 발생한다.")
        @Test
        void throwsException_whenNoLocalPart() {
            // act & assert
            CoreException exception = assertThrows(CoreException.class, () -> {
                new Email("@example.com");
            });

            assertThat(exception.getErrorType()).isEqualTo(UserErrorType.INVALID_EMAIL);
        }

        @DisplayName("연속 점이 포함되면, 예외가 발생한다.")
        @Test
        void throwsException_whenConsecutiveDots() {
            // act & assert
            CoreException exception = assertThrows(CoreException.class, () -> {
                new Email("nahyeon..lim@example.com");
            });

            assertThat(exception.getErrorType()).isEqualTo(UserErrorType.INVALID_EMAIL);
        }

        @DisplayName("공백이 포함되면, 예외가 발생한다.")
        @Test
        void throwsException_whenContainsSpace() {
            // act & assert
            CoreException exception = assertThrows(CoreException.class, () -> {
                new Email("nahyeon lim@example.com");
            });

            assertThat(exception.getErrorType()).isEqualTo(UserErrorType.INVALID_EMAIL);
        }

        @DisplayName("256자를 초과하면, 예외가 발생한다.")
        @Test
        void throwsException_whenExceedsMaxLength() {
            // arrange
            String value = "a".repeat(250) + "@b.com";  // 256자

            // act & assert
            CoreException exception = assertThrows(CoreException.class, () -> {
                new Email(value);
            });

            assertThat(exception.getErrorType()).isEqualTo(UserErrorType.INVALID_EMAIL);
        }

        @DisplayName("한글이 포함되면, 예외가 발생한다.")
        @Test
        void throwsException_whenContainsKorean() {
            // act & assert
            CoreException exception = assertThrows(CoreException.class, () -> {
                new Email("홍길동@example.com");
            });

            assertThat(exception.getErrorType()).isEqualTo(UserErrorType.INVALID_EMAIL);
        }
    }
}
