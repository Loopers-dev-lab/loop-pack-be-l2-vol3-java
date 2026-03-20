package com.loopers.support.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CardType 열거형 테스트")
class CardTypeTest {

    @Test
    @DisplayName("SAMSUNG, KB, HYUNDAI 3개 값이 존재한다")
    void values_ShouldContain_AllThreeCardTypes() {
        assertThat(CardType.values())
                .containsExactlyInAnyOrder(
                        CardType.SAMSUNG,
                        CardType.KB,
                        CardType.HYUNDAI
                );
    }

    @Test
    @DisplayName("valueOf로 각 카드 타입을 조회할 수 있다")
    void valueOf_ShouldReturn_CorrectCardType() {
        assertThat(CardType.valueOf("SAMSUNG")).isEqualTo(CardType.SAMSUNG);
        assertThat(CardType.valueOf("KB")).isEqualTo(CardType.KB);
        assertThat(CardType.valueOf("HYUNDAI")).isEqualTo(CardType.HYUNDAI);
    }
}
