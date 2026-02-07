package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.UserErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
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
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
public class EmailTest {

    @DisplayName("이메일을 생성할 때,")
    @Nested
    class Create {

        // ========== 정상 케이스 ==========

        @Test
        void 유효한_이메일이면_정상적으로_생성된다() {
            // arrange
            String value = "nahyeon@example.com";

            // act
            Email email = new Email(value);

            // assert
            assertThat(email.getValue()).isEqualTo(value);
        }

        @Test
        void 서브도메인이_있으면_정상적으로_생성된다() {
            // arrange
            String value = "nahyeon@mail.example.com";

            // act
            Email email = new Email(value);

            // assert
            assertThat(email.getValue()).isEqualTo(value);
        }

        @Test
        void 플러스_기호가_포함되면_정상적으로_생성된다() {
            // arrange
            String value = "nahyeon+tag@example.com";

            // act
            Email email = new Email(value);

            // assert
            assertThat(email.getValue()).isEqualTo(value);
        }

        // ========== 엣지 케이스 ==========

        @Test
        void null이면_예외가_발생한다() {
            // act & assert
            CoreException exception = assertThrows(CoreException.class, () -> {
                new Email(null);
            });

            assertThat(exception.getErrorType()).isEqualTo(UserErrorType.INVALID_EMAIL);
        }

        @Test
        void 빈_문자열이면_예외가_발생한다() {
            // act & assert
            CoreException exception = assertThrows(CoreException.class, () -> {
                new Email("");
            });

            assertThat(exception.getErrorType()).isEqualTo(UserErrorType.INVALID_EMAIL);
        }

        @Test
        void 골뱅이가_없으면_예외가_발생한다() {
            // act & assert
            CoreException exception = assertThrows(CoreException.class, () -> {
                new Email("nahyeonexample.com");
            });

            assertThat(exception.getErrorType()).isEqualTo(UserErrorType.INVALID_EMAIL);
        }

        @Test
        void 도메인이_없으면_예외가_발생한다() {
            // act & assert
            CoreException exception = assertThrows(CoreException.class, () -> {
                new Email("nahyeon@");
            });

            assertThat(exception.getErrorType()).isEqualTo(UserErrorType.INVALID_EMAIL);
        }

        @Test
        void 로컬_파트가_없으면_예외가_발생한다() {
            // act & assert
            CoreException exception = assertThrows(CoreException.class, () -> {
                new Email("@example.com");
            });

            assertThat(exception.getErrorType()).isEqualTo(UserErrorType.INVALID_EMAIL);
        }

        @Test
        void 연속_점이_포함되면_예외가_발생한다() {
            // act & assert
            CoreException exception = assertThrows(CoreException.class, () -> {
                new Email("nahyeon..lim@example.com");
            });

            assertThat(exception.getErrorType()).isEqualTo(UserErrorType.INVALID_EMAIL);
        }

        @Test
        void 공백이_포함되면_예외가_발생한다() {
            // act & assert
            CoreException exception = assertThrows(CoreException.class, () -> {
                new Email("nahyeon lim@example.com");
            });

            assertThat(exception.getErrorType()).isEqualTo(UserErrorType.INVALID_EMAIL);
        }

        @Test
        void 256자를_초과하면_예외가_발생한다() {
            // arrange
            String value = "a".repeat(250) + "@b.com";  // 256자

            // act & assert
            CoreException exception = assertThrows(CoreException.class, () -> {
                new Email(value);
            });

            assertThat(exception.getErrorType()).isEqualTo(UserErrorType.INVALID_EMAIL);
        }

        @Test
        void 한글이_포함되면_예외가_발생한다() {
            // act & assert
            CoreException exception = assertThrows(CoreException.class, () -> {
                new Email("홍길동@example.com");
            });

            assertThat(exception.getErrorType()).isEqualTo(UserErrorType.INVALID_EMAIL);
        }
    }
}
