package com.loopers.domain.user;

import com.loopers.domain.user.vo.LoginId;
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
 * LoginId Value Object 단위 테스트
 *
 * 검증 규칙:
 * - 영문 대소문자 + 숫자만 허용
 * - 4~20자
 * - 영문으로 시작
 */
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
public class LoginIdTest {

    @DisplayName("로그인 ID를 생성할 때,")
    @Nested
    class Create {

        // ========== 정상 케이스 ==========

        @Test
        void 최소_길이_4자_영문이면_정상적으로_생성된다() {
            // arrange
            String value = "nahyeon";

            // act
            LoginId loginId = new LoginId(value);

            // assert
            assertThat(loginId.getValue()).isEqualTo(value);
        }

        @Test
        void 최대_길이_20자이면_정상적으로_생성된다() {
            // arrange
            String value = "abcdefghij1234567890";  // 20자

            // act
            LoginId loginId = new LoginId(value);

            // assert
            assertThat(loginId.getValue()).isEqualTo(value);
        }

        @Test
        void 영문_대소문자_숫자_조합이면_정상적으로_생성된다() {
            // arrange
            String value = "nahyeon123";

            // act
            LoginId loginId = new LoginId(value);

            // assert
            assertThat(loginId.getValue()).isEqualTo(value);
        }

        // ========== 엣지 케이스 ==========

        @Test
        void null이면_예외가_발생한다() {
            // act & assert
            CoreException exception = assertThrows(CoreException.class, () -> {
                new LoginId(null);
            });

            assertThat(exception.getErrorType()).isEqualTo(UserErrorType.INVALID_LOGIN_ID);
        }

        @Test
        void 빈_문자열이면_예외가_발생한다() {
            // act & assert
            CoreException exception = assertThrows(CoreException.class, () -> {
                new LoginId("");
            });

            assertThat(exception.getErrorType()).isEqualTo(UserErrorType.INVALID_LOGIN_ID);
        }

        @Test
        void 공백만_있으면_예외가_발생한다() {
            // act & assert
            CoreException exception = assertThrows(CoreException.class, () -> {
                new LoginId("   ");
            });

            assertThat(exception.getErrorType()).isEqualTo(UserErrorType.INVALID_LOGIN_ID);
        }

        @Test
        void 길이가_3자_최소_미만이면_예외가_발생한다() {
            // act & assert
            CoreException exception = assertThrows(CoreException.class, () -> {
                new LoginId("abc");
            });

            assertThat(exception.getErrorType()).isEqualTo(UserErrorType.INVALID_LOGIN_ID);
        }

        @Test
        void 길이가_21자_최대_초과이면_예외가_발생한다() {
            // arrange
            String value = "abcdefghij12345678901";  // 21자

            // act & assert
            CoreException exception = assertThrows(CoreException.class, () -> {
                new LoginId(value);
            });

            assertThat(exception.getErrorType()).isEqualTo(UserErrorType.INVALID_LOGIN_ID);
        }

        @Test
        void 특수문자가_포함되면_예외가_발생한다() {
            // act & assert
            CoreException exception = assertThrows(CoreException.class, () -> {
                new LoginId("nahyeon@123");
            });

            assertThat(exception.getErrorType()).isEqualTo(UserErrorType.INVALID_LOGIN_ID);
        }

        @Test
        void 한글이_포함되면_예외가_발생한다() {
            // act & assert
            CoreException exception = assertThrows(CoreException.class, () -> {
                new LoginId("nahyeon홍");
            });

            assertThat(exception.getErrorType()).isEqualTo(UserErrorType.INVALID_LOGIN_ID);
        }

        @Test
        void 숫자로_시작하면_예외가_발생한다() {
            // act & assert
            CoreException exception = assertThrows(CoreException.class, () -> {
                new LoginId("123nahyeon");
            });

            assertThat(exception.getErrorType()).isEqualTo(UserErrorType.INVALID_LOGIN_ID);
        }

        @Test
        void 공백이_포함되면_예외가_발생한다() {
            // act & assert
            CoreException exception = assertThrows(CoreException.class, () -> {
                new LoginId("nahyeon Lim");
            });

            assertThat(exception.getErrorType()).isEqualTo(UserErrorType.INVALID_LOGIN_ID);
        }
    }
}
