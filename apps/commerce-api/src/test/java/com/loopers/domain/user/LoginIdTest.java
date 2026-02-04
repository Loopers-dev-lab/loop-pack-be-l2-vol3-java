package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.UserErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 - * Red Phase
 - *  1. 먼저 유효한 ID 생성 성공 테스트 작성 Happy Case
 - *  2. 테스트 실행 → 컴파일 에러 확인 (LoginId 클래스 없음)
 - *  3. Red 상태를 진행해봄! - 컴파일 에러 발생 근데 일단 이 컴파일 에러는 TDD에서의 시작이라
 - *  시작이 중요한게 아니라 테스트 케이스를 작성한 다음에 메인 코드가 작성이 잘되는게 테스트 코드 작성의 꽃이다.
 - *  테스트 케이스를 작성하다보면 너무 많아지는데 이 상황에서는 AB CD DE ... 무수히 많은 테스트 코드
 - *  그래서 깔끔하고 응집도 높게 집중할 수 있는 테스트 케이스를 작성하고 검증할 수 있는 테스트 케이스를 작성하면 객체 지향을 준수한
 - *  메인 테스트 코드를 만들 수 있다고 생각한다. 테스트 코드의 장점이다.
 - *  테스트 커버리지ㄴ를 올리기보단 ..
 - *  객체지향적 프로그래밍을 고려해서 테스트 코드를 진행하고싶음
 + * LoginId Value Object 단위 테스트
 *
 - *  스순환참조가 발생될 수 있는데 서비스간 함수를 두자 상위 레이어로 파사드란 레이어를 위치한다.
 - *  A 파사드는 A 서비스를 호출하면서도 의존하면서도 서비스를 의존받을 수 있다.
 - *  파사드 패턴이란 무엇일가
 - *
 - *  테스트 코드를 작성하기 이전에 설계부터 진행하는게 맞다고 생각해서 일다 나는 설계부터 진행
 - *
 - *  검증 로직은 아직 넣지 않음
 - *
 - *   껍데기 단계에서는 단순 할당만 수행
 - *   → 테스트 작성 후 Red 상태 확인
 - *   → 그 다음 검증 로직 추가 (Green)


/**
 * LoginId Value Object 단위 테스트
 *
 * 검증 규칙:
 * - 영문 대소문자 + 숫자만 허용
 * - 4~20자
 * - 영문으로 시작
 */
public class LoginIdTest {

    @DisplayName("로그인 ID를 생성할 때,")
    @Nested
    class Create {

        // ========== 정상 케이스 ==========

        @DisplayName("최소 길이(4자) 영문이면, 정상적으로 생성된다.")
        @Test
        void createsLoginId_whenMinLength() {
            // arrange
            String value = "nahyeon";

            // act
            LoginId loginId = new LoginId(value);

            // assert
            assertThat(loginId.getValue()).isEqualTo(value);
        }

        @DisplayName("최대 길이(20자)이면, 정상적으로 생성된다.")
        @Test
        void createsLoginId_whenMaxLength() {
            // arrange
            String value = "abcdefghij1234567890";  // 20자

            // act
            LoginId loginId = new LoginId(value);

            // assert
            assertThat(loginId.getValue()).isEqualTo(value);
        }

        @DisplayName("영문 대소문자 + 숫자 조합이면, 정상적으로 생성된다.")
        @Test
        void createsLoginId_whenAlphanumeric() {
            // arrange
            String value = "nahyeon123";

            // act
            LoginId loginId = new LoginId(value);

            // assert
            assertThat(loginId.getValue()).isEqualTo(value);
        }

        // ========== 엣지 케이스 ==========

        @DisplayName("null이면, 예외가 발생한다.")
        @Test
        void throwsException_whenNull() {
            // act & assert
            CoreException exception = assertThrows(CoreException.class, () -> {
                new LoginId(null);
            });

            assertThat(exception.getErrorType()).isEqualTo(UserErrorType.INVALID_LOGIN_ID);
        }

        @DisplayName("빈 문자열이면, 예외가 발생한다.")
        @Test
        void throwsException_whenEmpty() {
            // act & assert
            CoreException exception = assertThrows(CoreException.class, () -> {
                new LoginId("");
            });

            assertThat(exception.getErrorType()).isEqualTo(UserErrorType.INVALID_LOGIN_ID);
        }

        @DisplayName("공백만 있으면, 예외가 발생한다.")
        @Test
        void throwsException_whenBlank() {
            // act & assert
            CoreException exception = assertThrows(CoreException.class, () -> {
                new LoginId("   ");
            });

            assertThat(exception.getErrorType()).isEqualTo(UserErrorType.INVALID_LOGIN_ID);
        }

        @DisplayName("3자(최소 미만)이면, 예외가 발생한다.")
        @Test
        void throwsException_whenLessThanMinLength() {
            // act & assert
            CoreException exception = assertThrows(CoreException.class, () -> {
                new LoginId("abc");
            });

            assertThat(exception.getErrorType()).isEqualTo(UserErrorType.INVALID_LOGIN_ID);
        }

        @DisplayName("21자(최대 초과)이면, 예외가 발생한다.")
        @Test
        void throwsException_whenExceedsMaxLength() {
            // arrange
            String value = "abcdefghij12345678901";  // 21자

            // act & assert
            CoreException exception = assertThrows(CoreException.class, () -> {
                new LoginId(value);
            });

            assertThat(exception.getErrorType()).isEqualTo(UserErrorType.INVALID_LOGIN_ID);
        }

        @DisplayName("특수문자가 포함되면, 예외가 발생한다.")
        @Test
        void throwsException_whenContainsSpecialCharacter() {
            // act & assert
            CoreException exception = assertThrows(CoreException.class, () -> {
                new LoginId("nahyeon@123");
            });

            assertThat(exception.getErrorType()).isEqualTo(UserErrorType.INVALID_LOGIN_ID);
        }

        @DisplayName("한글이 포함되면, 예외가 발생한다.")
        @Test
        void throwsException_whenContainsKorean() {
            // act & assert
            CoreException exception = assertThrows(CoreException.class, () -> {
                new LoginId("nahyeon홍");
            });

            assertThat(exception.getErrorType()).isEqualTo(UserErrorType.INVALID_LOGIN_ID);
        }

        @DisplayName("숫자로 시작하면, 예외가 발생한다.")
        @Test
        void throwsException_whenStartsWithNumber() {
            // act & assert
            CoreException exception = assertThrows(CoreException.class, () -> {
                new LoginId("123nahyeon");
            });

            assertThat(exception.getErrorType()).isEqualTo(UserErrorType.INVALID_LOGIN_ID);
        }

        @DisplayName("공백이 포함되면, 예외가 발생한다.")
        @Test
        void throwsException_whenContainsSpace() {
            // act & assert
            CoreException exception = assertThrows(CoreException.class, () -> {
                new LoginId("nahyeon Lim");
            });

            assertThat(exception.getErrorType()).isEqualTo(UserErrorType.INVALID_LOGIN_ID);
        }
    }
}
