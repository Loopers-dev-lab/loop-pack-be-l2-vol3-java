package com.loopers.infrastructure.payment;

import com.loopers.support.enums.CardType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PgPaymentRequest 테스트")
class PgPaymentRequestTest {

    @Test
    @DisplayName("from()으로 domain 타입에서 PG 스펙 요청 DTO를 생성한다")
    void from_WithDomainTypes_ShouldCreatePgRequest() {
        PgPaymentRequest request = PgPaymentRequest.from(
                1L, CardType.SAMSUNG, "1234-5678-9012-3456",
                BigDecimal.valueOf(50000), "http://localhost:8080/callback"
        );

        assertThat(request.orderId()).isEqualTo("000001");
        assertThat(request.cardType()).isEqualTo("SAMSUNG");
        assertThat(request.cardNo()).isEqualTo("1234-5678-9012-3456");
        assertThat(request.amount()).isEqualByComparingTo(BigDecimal.valueOf(50000));
        assertThat(request.callbackUrl()).isEqualTo("http://localhost:8080/callback");
    }

    @Test
    @DisplayName("CardType enum이 String으로 변환된다")
    void from_WithKbCardType_ShouldConvertToString() {
        PgPaymentRequest request = PgPaymentRequest.from(
                2L, CardType.KB, "1111-2222-3333-4444",
                BigDecimal.valueOf(10000), "http://localhost:8080/callback"
        );

        assertThat(request.cardType()).isEqualTo("KB");
    }
}
