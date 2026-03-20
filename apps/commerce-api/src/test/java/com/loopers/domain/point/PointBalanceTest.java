package com.loopers.domain.point;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PointBalanceTest {

    @Test
    @DisplayName("포인트 사용 시 잔액이 감소한다")
    void usePoints() {
        PointBalance pointBalance = new PointBalance("point-user", 10000);

        PointBalance used = pointBalance.use(3000);

        assertThat(used.balance()).isEqualTo(7000);
    }

    @Test
    @DisplayName("포인트가 부족하면 예외가 발생한다")
    void usePointsFailsWhenInsufficient() {
        PointBalance pointBalance = new PointBalance("point-user", 1000);

        assertThatThrownBy(() -> pointBalance.use(2000))
                .isInstanceOf(CoreException.class)
                .satisfies(ex -> assertThat(((CoreException) ex).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));
    }

    @Test
    @DisplayName("포인트 복구 시 잔액이 증가한다")
    void restorePoints() {
        PointBalance pointBalance = new PointBalance("point-user", 1000);

        PointBalance restored = pointBalance.restore(2000);

        assertThat(restored.balance()).isEqualTo(3000);
    }
}
