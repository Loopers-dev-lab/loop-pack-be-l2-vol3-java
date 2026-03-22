package com.loopers.infrastructure.payment;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PgSimulatorRequestTest {

    @Test
    @DisplayName("toString에는 카드번호(cardNo)가 평문으로 노출되지 않는다.")
    void toString_shouldMaskCardNo() {
        String cardNo = "1234-5678-9814-1451";

        PgSimulatorRequest request = new PgSimulatorRequest(
                1L,
                "SAMSUNG",
                cardNo,
                10000L,
                "http://cb"
        );

        String str = request.toString();

        assertThat(str).doesNotContain(cardNo);
        assertThat(str).contains("orderId=1");
        assertThat(str).contains("cardType=SAMSUNG");
    }
}

