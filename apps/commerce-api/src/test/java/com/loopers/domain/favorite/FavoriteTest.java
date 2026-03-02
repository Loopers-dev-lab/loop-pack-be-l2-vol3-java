package com.loopers.domain.favorite;

import com.loopers.domain.favorite.model.Favorite;
import com.loopers.support.error.CoreException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

class FavoriteTest {

    @DisplayName("좋아요 생성")
    @Nested
    class Create {

        @DisplayName("유효한 값이 주어지면, 정상적으로 생성된다.")
        @Test
        void createsFavorite_whenValid() {
            // act
            Favorite favorite = Favorite.create(1L, 1L);

            // assert
            assertAll(
                () -> assertThat(favorite.getId()).isNull(),
                () -> assertThat(favorite.getMemberId()).isEqualTo(1L),
                () -> assertThat(favorite.getProductId()).isEqualTo(1L)
            );
        }

        @DisplayName("회원 ID가 null이면 예외가 발생한다.")
        @Test
        void throwsException_whenMemberIdIsNull() {
            // act & assert
            assertThatThrownBy(() -> Favorite.create(null, 1L))
                .isInstanceOf(CoreException.class);
        }

        @DisplayName("상품 ID가 null이면 예외가 발생한다.")
        @Test
        void throwsException_whenProductIdIsNull() {
            // act & assert
            assertThatThrownBy(() -> Favorite.create(1L, null))
                .isInstanceOf(CoreException.class);
        }
    }

    @DisplayName("DB에서 복원할 때, ")
    @Nested
    class Reconstruct {

        @DisplayName("id를 포함한 모든 필드가 올바르게 복원된다.")
        @Test
        void reconstructsFavorite_withAllFields() {
            // act
            Favorite favorite = Favorite.reconstruct(1L, 1L, 1L);

            // assert
            assertAll(
                () -> assertThat(favorite.getId()).isEqualTo(1L),
                () -> assertThat(favorite.getMemberId()).isEqualTo(1L),
                () -> assertThat(favorite.getProductId()).isEqualTo(1L)
            );
        }
    }
}
