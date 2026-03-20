package com.loopers.infrastructure.payment;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 8(타입 안전): PG 조회 API 반환이 구체 DTO인지 검증 (06 §14, checklist).
 */
class PgSimulatorClientReturnTypeTest {

    @Test
    @DisplayName("getPaymentStatus는 PgPaymentStatusResponse를 반환한다.")
    void getPaymentStatus_returnType_shouldBePgPaymentStatusResponse() throws Exception {
        // given
        Method m = PgSimulatorClient.class.getMethod("getPaymentStatus", String.class);
        // when / then
        assertThat(m.getReturnType()).isEqualTo(PgPaymentStatusResponse.class);
    }

    @Test
    @DisplayName("getPaymentsByOrderId는 PgPaymentStatusResponse를 반환한다.")
    void getPaymentsByOrderId_returnType_shouldBePgPaymentStatusResponse() throws Exception {
        // given
        Method m = PgSimulatorClient.class.getMethod("getPaymentsByOrderId", Long.class);
        // when / then
        assertThat(m.getReturnType()).isEqualTo(PgPaymentStatusResponse.class);
    }
}
