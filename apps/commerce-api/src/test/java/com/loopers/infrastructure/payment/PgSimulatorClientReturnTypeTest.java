package com.loopers.infrastructure.payment;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 역할: Feign 인터페이스 {@link PgSimulatorClient}의 조회 메서드가 Object/raw 타입이 아닌
 * {@link PgPaymentStatusResponse}로 선언돼 있는지 리플렉션으로 고정한다 (리그레션·문서 06 §14).
 */
class PgSimulatorClientReturnTypeTest {

    /** 단건 결제 상태 조회 시 반환 타입이 구체 DTO인지 확인. */
    @Test
    @DisplayName("getPaymentStatus는 PgPaymentStatusResponse를 반환한다.")
    void getPaymentStatus_returnType_shouldBePgPaymentStatusResponse() throws Exception {
        // given
        Method m = PgSimulatorClient.class.getMethod("getPaymentStatus", String.class);
        // when / then
        assertThat(m.getReturnType()).isEqualTo(PgPaymentStatusResponse.class);
    }

    /** 주문 기준 조회(복구 폴링) API도 동일 DTO로 역직렬화되도록 시그니처 고정. */
    @Test
    @DisplayName("getPaymentsByOrderId는 PgPaymentStatusResponse를 반환한다.")
    void getPaymentsByOrderId_returnType_shouldBePgPaymentStatusResponse() throws Exception {
        // given
        Method m = PgSimulatorClient.class.getMethod("getPaymentsByOrderId", Long.class);
        // when / then
        assertThat(m.getReturnType()).isEqualTo(PgPaymentStatusResponse.class);
    }
}
