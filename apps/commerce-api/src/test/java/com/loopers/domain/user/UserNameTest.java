package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.UserErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * UserName Value Object 단위 테스트
 *
 * 검증 규칙:
 * - 한글, 영문만 허용
 * - 2~50자
 * - 공백 불허
 *
 * 마스킹 규칙:
 * - 마지막 1글자를 '*'로 대체
 */
public class UserNameTest {

    @DisplayName("이름을 생성할 때,")
    @Nested
    class Create {

        // ========== 정상 케이스 ==========

        @DisplayName("한글 이름이면, 정상적으로 생성된다.")
        @Test
        void createsUserName_whenKorean() {
            // arrange
            String value = "홍길동";

            // act
            UserName userName = new UserName(value);

            // assert
            assertThat(userName.getValue()).isEqualTo(value);
        }

        @DisplayName("영문 이름이면, 정상적으로 생성된다.")
        @Test
        void createsUserName_whenEnglish() {
            // arrange
            String value = "Nahyeon";

            // act
            UserName userName = new UserName(value);

            // assert
            assertThat(userName.getValue()).isEqualTo(value);
        }

        @DisplayName("최소 길이(2자)이면, 정상적으로 생성된다.")
        @Test
        void createsUserName_whenMinLength() {
            // arrange
            String value = "홍길";

            // act
            UserName userName = new UserName(value);

            // assert
            assertThat(userName.getValue()).isEqualTo(value);
        }

        @DisplayName("최대 길이(50자)이면, 정상적으로 생성된다.")
        @Test
        void createsUserName_whenMaxLength() {
            // arrange
            String value = "가".repeat(50);

            // act
            UserName userName = new UserName(value);

            // assert
            assertThat(userName.getValue()).isEqualTo(value);
        }

        @DisplayName("한글+영문 혼합이면, 정상적으로 생성된다.")
        @Test
        void createsUserName_whenKoreanAndEnglishMixed() {
            // arrange
            String value = "홍Nahyeon";

            // act
            UserName userName = new UserName(value);

            // assert
            assertThat(userName.getValue()).isEqualTo(value);
        }

        // ========== 엣지 케이스 ==========

        @DisplayName("null이면, 예외가 발생한다.")
        @Test
        void throwsException_whenNull() {
            // act & assert
            CoreException exception = assertThrows(CoreException.class, () -> {
                new UserName(null);
            });

            assertThat(exception.getErrorType()).isEqualTo(UserErrorType.INVALID_NAME);
        }

        @DisplayName("빈 문자열이면, 예외가 발생한다.")
        @Test
        void throwsException_whenEmpty() {
            // act & assert
            CoreException exception = assertThrows(CoreException.class, () -> {
                new UserName("");
            });

            assertThat(exception.getErrorType()).isEqualTo(UserErrorType.INVALID_NAME);
        }

        @DisplayName("1자(최소 미만)이면, 예외가 발생한다.")
        @Test
        void throwsException_whenLessThanMinLength() {
            // act & assert
            CoreException exception = assertThrows(CoreException.class, () -> {
                new UserName("홍");
            });

            assertThat(exception.getErrorType()).isEqualTo(UserErrorType.INVALID_NAME);
        }

        @DisplayName("51자(최대 초과)이면, 예외가 발생한다.")
        @Test
        void throwsException_whenExceedsMaxLength() {
            // arrange
            String value = "가".repeat(51);

            // act & assert
            CoreException exception = assertThrows(CoreException.class, () -> {
                new UserName(value);
            });

            assertThat(exception.getErrorType()).isEqualTo(UserErrorType.INVALID_NAME);
        }

        @DisplayName("숫자가 포함되면, 예외가 발생한다.")
        @Test
        void throwsException_whenContainsDigits() {
            // act & assert
            CoreException exception = assertThrows(CoreException.class, () -> {
                new UserName("홍길동123");
            });

            assertThat(exception.getErrorType()).isEqualTo(UserErrorType.INVALID_NAME);
        }

        @DisplayName("특수문자가 포함되면, 예외가 발생한다.")
        @Test
        void throwsException_whenContainsSpecialChars() {
            // act & assert
            CoreException exception = assertThrows(CoreException.class, () -> {
                new UserName("홍길동!");
            });

            assertThat(exception.getErrorType()).isEqualTo(UserErrorType.INVALID_NAME);
        }

        @DisplayName("공백이 포함되면, 예외가 발생한다.")
        @Test
        void throwsException_whenContainsSpace() {
            // act & assert
            CoreException exception = assertThrows(CoreException.class, () -> {
                new UserName("홍 길동");
            });

            assertThat(exception.getErrorType()).isEqualTo(UserErrorType.INVALID_NAME);
        }
    }

    @DisplayName("이름을 마스킹할 때,")
    @Nested
    class Masking {

        @DisplayName("3자 한글이면, 마지막 글자가 *로 대체된다.")
        @Test
        void masksLastChar_whenThreeCharKorean() {
            // arrange
            UserName userName = new UserName("홍길동");

            // act
            String masked = userName.getMaskedValue();

            // assert
            assertThat(masked).isEqualTo("홍길*");
        }

        @DisplayName("2자 한글이면, 마지막 글자가 *로 대체된다.")
        @Test
        void masksLastChar_whenTwoCharKorean() {
            // arrange
            UserName userName = new UserName("홍길");

            // act
            String masked = userName.getMaskedValue();

            // assert
            assertThat(masked).isEqualTo("홍*");
        }

        @DisplayName("영문이면, 마지막 글자가 *로 대체된다.")
        @Test
        void masksLastChar_whenEnglish() {
            // arrange
            UserName userName = new UserName("Nahyeon");

            // act
            String masked = userName.getMaskedValue();

            // assert
            assertThat(masked).isEqualTo("Nahyeo*");
        }
    }
}
