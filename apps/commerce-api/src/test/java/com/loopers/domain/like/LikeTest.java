package com.loopers.domain.like;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LikeTest {

    @DisplayName("Like 생성 시 memberId, productId, createdAt이 설정된다")
    @Test
    void create_withMemberAndProduct_setsFields() {
        Like like = new Like(1L, 100L);

        assertThat(like.getMemberId()).isEqualTo(1L);
        assertThat(like.getProductId()).isEqualTo(100L);
        assertThat(like.getCreatedAt()).isNotNull();
    }
}
