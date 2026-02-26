package com.loopers.domain.model.order;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DeliveryInfoTest {

    @Test
    @DisplayName("DeliveryInfo 생성 성공")
    void of_success() {
        DeliveryInfo info = DeliveryInfo.of(
                "홍길동",
                "서울시 강남구",
                "문 앞에 놓아주세요",
                LocalDate.of(2025, 6, 15)
        );

        assertThat(info.getReceiverName()).isEqualTo("홍길동");
        assertThat(info.getAddress()).isEqualTo("서울시 강남구");
        assertThat(info.getDeliveryRequest()).isEqualTo("문 앞에 놓아주세요");
        assertThat(info.getDesiredDeliveryDate()).isEqualTo(LocalDate.of(2025, 6, 15));
    }

    @Test
    @DisplayName("배송 요청사항과 희망 배송일은 nullable")
    void of_nullable_fields() {
        DeliveryInfo info = DeliveryInfo.of(
                "홍길동",
                "서울시",
                null,
                null
        );

        assertThat(info.getDeliveryRequest()).isNull();
        assertThat(info.getDesiredDeliveryDate()).isNull();
    }

    @Test
    @DisplayName("수령인 이름이 null이면 예외")
    void of_fail_null_receiverName() {
        assertThatThrownBy(() -> DeliveryInfo.of(null, "서울시", null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("수령인 이름은 필수입니다");
    }

    @Test
    @DisplayName("배송 주소가 null이면 예외")
    void of_fail_null_address() {
        assertThatThrownBy(() -> DeliveryInfo.of("홍길동", null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("배송 주소는 필수입니다");
    }

    @Test
    @DisplayName("withAddress로 새 배송지 반환 - 불변 객체")
    void withAddress_returns_new_instance() {
        DeliveryInfo original = DeliveryInfo.of(
                "홍길동",
                "서울시",
                "요청사항",
                LocalDate.of(2025, 6, 15)
        );

        DeliveryInfo updated = original.withAddress("부산시");

        assertThat(updated.getAddress()).isEqualTo("부산시");
        assertThat(updated.getReceiverName()).isEqualTo("홍길동");
        assertThat(updated.getDeliveryRequest()).isEqualTo("요청사항");
        assertThat(updated.getDesiredDeliveryDate()).isEqualTo(LocalDate.of(2025, 6, 15));

        // 원본 불변 확인
        assertThat(original.getAddress()).isEqualTo("서울시");
    }
}
