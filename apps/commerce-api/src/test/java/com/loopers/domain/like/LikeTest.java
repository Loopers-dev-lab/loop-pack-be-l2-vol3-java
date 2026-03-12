package com.loopers.domain.like;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class LikeTest {

    @DisplayName("Like를 생성할 때,")
    @Nested
    class Create {

        @DisplayName("유효한 정보로 생성하면, 정상적으로 생성된다.")
        @Test
        void createsLike_whenInfoIsValid() {
            // arrange
            Long userId = 1L;
            Long productId = 100L;

            // act
            Like like = Like.create(userId, productId);

            // assert
            assertThat(like.getUserId()).isEqualTo(userId);
            assertThat(like.getProductId()).isEqualTo(productId);
            assertThat(like.getCreatedAt()).isNotNull();
        }

        @DisplayName("ID는 null로 생성된다.")
        @Test
        void createsWithNullId() {
            // act
            Like like = Like.create(1L, 100L);

            // assert
            assertThat(like.getId()).isNull();
        }
    }

    @DisplayName("Like를 복원할 때,")
    @Nested
    class Reconstitute {

        @DisplayName("모든 필드가 복원된다.")
        @Test
        void reconstitutesAllFields() {
            // arrange
            Long id = 1L;
            Long userId = 10L;
            Long productId = 100L;
            ZonedDateTime createdAt = ZonedDateTime.now().minusDays(1);

            // act
            Like like = Like.reconstitute(id, userId, productId, createdAt);

            // assert
            assertThat(like.getId()).isEqualTo(id);
            assertThat(like.getUserId()).isEqualTo(userId);
            assertThat(like.getProductId()).isEqualTo(productId);
            assertThat(like.getCreatedAt()).isEqualTo(createdAt);
        }
    }
}
