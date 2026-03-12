package com.loopers.domain.point;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UserPointTest {

    @DisplayName("포인트를 생성할 때,")
    @Nested
    class Create {

        @DisplayName("유효한 값으로 생성하면, 정상적으로 생성된다.")
        @Test
        void createsUserPoint_whenValuesAreValid() {
            // arrange
            Long userId = 1L;
            long initialBalance = 10000L;

            // act
            UserPoint point = UserPoint.create(userId, initialBalance);

            // assert
            assertThat(point.getUserId()).isEqualTo(userId);
            assertThat(point.getBalance()).isEqualTo(initialBalance);
            assertThat(point.getUpdatedAt()).isNotNull();
        }

        @DisplayName("초기 잔액이 0이면, 정상적으로 생성된다.")
        @Test
        void createsUserPoint_whenInitialBalanceIsZero() {
            // arrange
            Long userId = 1L;

            // act
            UserPoint point = UserPoint.create(userId, 0);

            // assert
            assertThat(point.getBalance()).isEqualTo(0);
        }

        @DisplayName("초기 잔액이 음수이면, 예외가 발생한다.")
        @Test
        void throwsException_whenInitialBalanceIsNegative() {
            // arrange
            Long userId = 1L;
            long negativeBalance = -1000L;

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                UserPoint.create(userId, negativeBalance);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("포인트를 충전할 때,")
    @Nested
    class Charge {

        @DisplayName("유효한 금액을 충전하면, 잔액이 증가한다.")
        @Test
        void increasesBalance_whenChargeAmountIsValid() {
            // arrange
            UserPoint point = UserPoint.create(1L, 5000L);

            // act
            point.charge(3000L);

            // assert
            assertThat(point.getBalance()).isEqualTo(8000L);
        }

        @DisplayName("충전 금액이 0이면, 예외가 발생한다.")
        @Test
        void throwsException_whenChargeAmountIsZero() {
            // arrange
            UserPoint point = UserPoint.create(1L, 5000L);

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                point.charge(0);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("충전 금액이 음수이면, 예외가 발생한다.")
        @Test
        void throwsException_whenChargeAmountIsNegative() {
            // arrange
            UserPoint point = UserPoint.create(1L, 5000L);

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                point.charge(-1000);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("포인트를 사용할 때,")
    @Nested
    class Use {

        @DisplayName("잔액이 충분하면, 잔액이 감소한다.")
        @Test
        void decreasesBalance_whenSufficientBalance() {
            // arrange
            UserPoint point = UserPoint.create(1L, 10000L);

            // act
            point.use(3000L);

            // assert
            assertThat(point.getBalance()).isEqualTo(7000L);
        }

        @DisplayName("잔액이 정확히 같으면, 잔액이 0이 된다.")
        @Test
        void becomesZero_whenUsingExactBalance() {
            // arrange
            UserPoint point = UserPoint.create(1L, 5000L);

            // act
            point.use(5000L);

            // assert
            assertThat(point.getBalance()).isEqualTo(0L);
        }

        @DisplayName("잔액이 부족하면, 예외가 발생한다.")
        @Test
        void throwsException_whenInsufficientBalance() {
            // arrange
            UserPoint point = UserPoint.create(1L, 3000L);

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                point.use(5000L);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.INSUFFICIENT_POINT);
        }

        @DisplayName("사용 금액이 0이면, 예외가 발생한다.")
        @Test
        void throwsException_whenUseAmountIsZero() {
            // arrange
            UserPoint point = UserPoint.create(1L, 5000L);

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                point.use(0);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("사용 금액이 음수이면, 예외가 발생한다.")
        @Test
        void throwsException_whenUseAmountIsNegative() {
            // arrange
            UserPoint point = UserPoint.create(1L, 5000L);

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                point.use(-1000);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("잔액 충분 여부를 확인할 때,")
    @Nested
    class HasEnough {

        @DisplayName("잔액이 충분하면, true를 반환한다.")
        @Test
        void returnsTrue_whenSufficientBalance() {
            // arrange
            UserPoint point = UserPoint.create(1L, 10000L);

            // act & assert
            assertThat(point.hasEnough(5000L)).isTrue();
        }

        @DisplayName("잔액이 정확히 같으면, true를 반환한다.")
        @Test
        void returnsTrue_whenExactBalance() {
            // arrange
            UserPoint point = UserPoint.create(1L, 5000L);

            // act & assert
            assertThat(point.hasEnough(5000L)).isTrue();
        }

        @DisplayName("잔액이 부족하면, false를 반환한다.")
        @Test
        void returnsFalse_whenInsufficientBalance() {
            // arrange
            UserPoint point = UserPoint.create(1L, 3000L);

            // act & assert
            assertThat(point.hasEnough(5000L)).isFalse();
        }
    }
}
