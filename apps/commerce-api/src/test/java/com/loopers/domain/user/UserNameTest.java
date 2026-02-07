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
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
public class UserNameTest {

    @DisplayName("이름을 생성할 때,")
    @Nested
    class Create {

        // ========== 정상 케이스 ==========

        @Test
        void 한글_이름이면_정상적으로_생성된다() {
            // arrange
            String value = "홍길동";

            // act
            UserName userName = new UserName(value);

            // assert
            assertThat(userName.getValue()).isEqualTo(value);
        }

        @Test
        void 영문_이름이면_정상적으로_생성된다() {
            // arrange
            String value = "Nahyeon";

            // act
            UserName userName = new UserName(value);

            // assert
            assertThat(userName.getValue()).isEqualTo(value);
        }

        @Test
        void 최소_길이_2자이면_정상적으로_생성된다() {
            // arrange
            String value = "홍길";

            // act
            UserName userName = new UserName(value);

            // assert
            assertThat(userName.getValue()).isEqualTo(value);
        }

        @Test
        void 최대_길이_50자이면_정상적으로_생성된다() {
            // arrange
            String value = "가".repeat(50);

            // act
            UserName userName = new UserName(value);

            // assert
            assertThat(userName.getValue()).isEqualTo(value);
        }

        @Test
        void 한글_영문_혼합이면_정상적으로_생성된다() {
            // arrange
            String value = "홍Nahyeon";

            // act
            UserName userName = new UserName(value);

            // assert
            assertThat(userName.getValue()).isEqualTo(value);
        }

        // ========== 엣지 케이스 ==========

        @Test
        void null이면_예외가_발생한다() {
            // act & assert
            CoreException exception = assertThrows(CoreException.class, () -> {
                new UserName(null);
            });

            assertThat(exception.getErrorType()).isEqualTo(UserErrorType.INVALID_NAME);
        }

        @Test
        void 빈_문자열이면_예외가_발생한다() {
            // act & assert
            CoreException exception = assertThrows(CoreException.class, () -> {
                new UserName("");
            });

            assertThat(exception.getErrorType()).isEqualTo(UserErrorType.INVALID_NAME);
        }

        @Test
        void 1자_최소_미만이면_예외가_발생한다() {
            // act & assert
            CoreException exception = assertThrows(CoreException.class, () -> {
                new UserName("홍");
            });

            assertThat(exception.getErrorType()).isEqualTo(UserErrorType.INVALID_NAME);
        }

        @Test
        void 51자_최대_초과이면_예외가_발생한다() {
            // arrange
            String value = "가".repeat(51);

            // act & assert
            CoreException exception = assertThrows(CoreException.class, () -> {
                new UserName(value);
            });

            assertThat(exception.getErrorType()).isEqualTo(UserErrorType.INVALID_NAME);
        }

        @Test
        void 숫자가_포함되면_예외가_발생한다() {
            // act & assert
            CoreException exception = assertThrows(CoreException.class, () -> {
                new UserName("홍길동123");
            });

            assertThat(exception.getErrorType()).isEqualTo(UserErrorType.INVALID_NAME);
        }

        @Test
        void 특수문자가_포함되면_예외가_발생한다() {
            // act & assert
            CoreException exception = assertThrows(CoreException.class, () -> {
                new UserName("홍길동!");
            });

            assertThat(exception.getErrorType()).isEqualTo(UserErrorType.INVALID_NAME);
        }

        @Test
        void 공백이_포함되면_예외가_발생한다() {
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

        @Test
        void 3자_한글이면_마지막_글자가_별표로_대체된다() {
            // arrange
            UserName userName = new UserName("홍길동");

            // act
            String masked = userName.getMaskedValue();

            // assert
            assertThat(masked).isEqualTo("홍길*");
        }

        @Test
        void 2자_한글이면_마지막_글자가_별표로_대체된다() {
            // arrange
            UserName userName = new UserName("홍길");

            // act
            String masked = userName.getMaskedValue();

            // assert
            assertThat(masked).isEqualTo("홍*");
        }

        @Test
        void 영문이면_마지막_글자가_별표로_대체된다() {
            // arrange
            UserName userName = new UserName("Nahyeon");

            // act
            String masked = userName.getMaskedValue();

            // assert
            assertThat(masked).isEqualTo("Nahyeo*");
        }
    }
}
