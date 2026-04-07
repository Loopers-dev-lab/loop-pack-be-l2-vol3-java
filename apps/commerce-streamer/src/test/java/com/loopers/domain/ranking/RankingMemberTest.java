package com.loopers.domain.ranking;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RankingMemberTest {

    @Test
    @DisplayName("productId를 member 문자열로 변환한다.")
    void fromProductId_whenPositiveId_shouldReturnStringMember() {
        // given
        long productId = 101L;

        // when
        String member = RankingMember.fromProductId(productId);

        // then
        assertThat(member).isEqualTo("101");
    }

    @Test
    @DisplayName("0 이하 productId는 member로 허용하지 않는다.")
    void fromProductId_whenNonPositive_shouldThrowIllegalArgumentException() {
        // given
        long productId = 0L;

        // when & then
        assertThatThrownBy(() -> RankingMember.fromProductId(productId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("productId");
    }
}
