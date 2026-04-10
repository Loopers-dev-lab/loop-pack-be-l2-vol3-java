package com.loopers.domain.like.event;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class LikeToggledEventTest {

    @DisplayName("좋아요 이벤트 생성 시 productId와 liked 상태를 가진다")
    @Test
    void createLikeToggledEvent() {
        // given
        Long productId = 1L;

        // when
        LikeToggledEvent event = new LikeToggledEvent(productId, true);

        // then
        assertThat(event.productId()).isEqualTo(1L);
        assertThat(event.liked()).isTrue();
    }
}
