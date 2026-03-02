package com.loopers.domain.point;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.PointErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class PointAccountTest {

    @DisplayName("생성할 때,")
    @Nested
    class 생성 {

        @Test
        void 생성_시_balance가_0이다() {
            // act
            PointAccount account = PointAccount.create(1L);

            // assert
            assertThat(account.getBalance()).isEqualTo(0);
        }
    }

    @DisplayName("충전할 때,")
    @Nested
    class 충전 {

        @Test
        void 금액이_0_이하이면_예외가_발생한다() {
            // arrange
            PointAccount account = PointAccount.create(1L);

            // act & assert
            assertThatThrownBy(() -> account.charge(0))
                    .isInstanceOf(CoreException.class)
                    .extracting(e -> ((CoreException) e).getErrorType())
                    .isEqualTo(PointErrorType.INVALID_AMOUNT);
        }

        @Test
        void 유효한_금액이면_balance가_증가한다() {
            // arrange
            PointAccount account = PointAccount.create(1L);

            // act
            account.charge(10000);

            // assert
            assertThat(account.getBalance()).isEqualTo(10000);
        }
    }

    @DisplayName("사용할 때,")
    @Nested
    class 사용 {

        @Test
        void 금액이_0_이하이면_예외가_발생한다() {
            // arrange
            PointAccount account = PointAccount.create(1L);

            // act & assert
            assertThatThrownBy(() -> account.use(0))
                    .isInstanceOf(CoreException.class)
                    .extracting(e -> ((CoreException) e).getErrorType())
                    .isEqualTo(PointErrorType.INVALID_AMOUNT);
        }

        @Test
        void 잔액이_부족하면_예외가_발생한다() {
            // arrange
            PointAccount account = PointAccount.create(1L);
            account.charge(5000);

            // act & assert
            assertThatThrownBy(() -> account.use(10000))
                    .isInstanceOf(CoreException.class)
                    .extracting(e -> ((CoreException) e).getErrorType())
                    .isEqualTo(PointErrorType.INSUFFICIENT_BALANCE);
        }

        @Test
        void 유효한_금액이면_balance가_감소한다() {
            // arrange
            PointAccount account = PointAccount.create(1L);
            account.charge(10000);

            // act
            account.use(3000);

            // assert
            assertThat(account.getBalance()).isEqualTo(7000);
        }
    }

    @DisplayName("환불할 때,")
    @Nested
    class 환불 {

        @Test
        void 금액이_0_이하이면_예외가_발생한다() {
            // arrange
            PointAccount account = PointAccount.create(1L);

            // act & assert
            assertThatThrownBy(() -> account.refund(0))
                    .isInstanceOf(CoreException.class)
                    .extracting(e -> ((CoreException) e).getErrorType())
                    .isEqualTo(PointErrorType.INVALID_AMOUNT);
        }

        @Test
        void 유효한_금액이면_balance가_증가한다() {
            // arrange
            PointAccount account = PointAccount.create(1L);

            // act
            account.refund(5000);

            // assert
            assertThat(account.getBalance()).isEqualTo(5000);
        }
    }
}
