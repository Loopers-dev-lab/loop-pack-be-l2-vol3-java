package com.loopers.domain.payment;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PgResult VO 테스트")
class PgResultTest {

    @Test
    @DisplayName("unavailable() 팩토리 메서드는 pgTransactionKey가 null이고 UNAVAILABLE 상태를 반환한다")
    void unavailable_returnsCorrectResult() {
        PgResult result = PgResult.unavailable();

        assertThat(result.pgTransactionKey()).isNull();
        assertThat(result.status()).isEqualTo(PgStatus.UNAVAILABLE);
        assertThat(result.isUnavailable()).isTrue();
    }

    @Test
    @DisplayName("PENDING 상태일 때 isAccepted()는 true를 반환한다")
    void isAccepted_whenPending_returnsTrue() {
        PgResult result = new PgResult("txKey", PgStatus.PENDING, null, null);

        assertThat(result.isAccepted()).isTrue();
        assertThat(result.isUnavailable()).isFalse();
    }

    @Test
    @DisplayName("SUCCESS 상태일 때 isSuccess()는 true를 반환한다")
    void isSuccess_whenSuccess_returnsTrue() {
        PgResult result = new PgResult("txKey", PgStatus.SUCCESS, "정상 승인되었습니다.", new BigDecimal("10000"));

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.isAccepted()).isFalse();
    }

    @Test
    @DisplayName("FAILED 상태일 때 isAccepted()와 isSuccess()는 false를 반환한다")
    void isAccepted_whenFailed_returnsFalse() {
        PgResult result = new PgResult("txKey", PgStatus.FAILED, "한도초과입니다. 다른 카드를 선택해주세요.", null);

        assertThat(result.isAccepted()).isFalse();
        assertThat(result.isSuccess()).isFalse();
    }
}
