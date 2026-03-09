package com.loopers.application.like;

import com.loopers.domain.like.LikeModel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LikeInfoTest {

    @Mock
    private LikeModel like;

    @DisplayName("from(LikeModel)")
    @Test
    void from_withNull_shouldThrowIllegalArgumentException() {
        assertThatThrownBy(() -> LikeInfo.from(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not be null");
    }

    @DisplayName("유효한 LikeModel이 주어지면 필드가 일치하는 LikeInfo를 반환한다.")
    @Test
    void from_withValidLike_shouldReturnLikeInfoWithMatchingFields() {
        // given
        Long id = 1L;
        Long userId = 10L;
        Long productId = 100L;
        ZonedDateTime createdAt = ZonedDateTime.now();
        when(like.getId()).thenReturn(id);
        when(like.getUserId()).thenReturn(userId);
        when(like.getProductId()).thenReturn(productId);
        when(like.getCreatedAt()).thenReturn(createdAt);

        // when
        LikeInfo info = LikeInfo.from(like);

        // then
        assertThat(info.id()).isEqualTo(id);
        assertThat(info.userId()).isEqualTo(userId);
        assertThat(info.productId()).isEqualTo(productId);
        assertThat(info.createdAt()).isEqualTo(createdAt);
    }
}
