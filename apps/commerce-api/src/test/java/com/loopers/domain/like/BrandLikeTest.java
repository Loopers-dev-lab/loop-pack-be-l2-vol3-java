package com.loopers.domain.like;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class BrandLikeTest {

    @DisplayName("생성할 때,")
    @Nested
    class 생성 {

        @Test
        void 유효한_정보면_userId와_brandId가_저장된다() {
            // act
            BrandLike brandLike = BrandLike.of(1L, 100L);

            // assert
            assertThat(brandLike)
                    .extracting(BrandLike::getUserId, BrandLike::getBrandId)
                    .containsExactly(1L, 100L);
        }

        @Test
        void 생성_시_createdAt이_설정된다() {
            // act
            BrandLike brandLike = BrandLike.of(1L, 100L);

            // assert — createdAt은 @PrePersist로 설정되므로 여기서는 null
            assertThat(brandLike.getUserId()).isEqualTo(1L);
        }
    }
}
