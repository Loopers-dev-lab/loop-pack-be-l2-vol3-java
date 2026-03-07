package com.loopers.infrastructure.cart;

import com.loopers.domain.cart.CartItem;
import com.loopers.domain.cart.CartItemRepository;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class CartItemRepositoryIntegrationTest {

    @Autowired
    private CartItemRepository cartItemRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @Nested
    @DisplayName("save 메서드는")
    class Save {

        @Test
        void 새로운_장바구니_항목을_저장하면_ID가_생성된다() {
            // arrange
            CartItem cartItem = CartItem.of(1L, 100L, 3);

            // act
            CartItem saved = cartItemRepository.save(cartItem);

            // assert
            assertThat(saved.getId()).isNotNull();
        }

        @Test
        void 저장된_항목의_필드가_올바르게_저장된다() {
            // arrange
            CartItem cartItem = CartItem.of(1L, 100L, 3);

            // act
            CartItem saved = cartItemRepository.save(cartItem);

            // assert
            assertThat(saved)
                    .extracting(CartItem::getUserId, CartItem::getProductId, CartItem::getQuantity)
                    .containsExactly(1L, 100L, 3);
        }
    }

    @Nested
    @DisplayName("findById 메서드는")
    class FindById {

        @Test
        void 존재하는_항목을_반환한다() {
            // arrange
            CartItem saved = cartItemRepository.save(CartItem.of(1L, 100L, 3));

            // act
            Optional<CartItem> result = cartItemRepository.findById(saved.getId());

            // assert
            assertThat(result).isPresent();
        }

        @Test
        void 소프트_삭제된_항목은_조회되지_않는다() {
            // arrange
            CartItem saved = cartItemRepository.save(CartItem.of(1L, 100L, 3));
            saved.remove();
            cartItemRepository.save(saved);

            // act
            Optional<CartItem> result = cartItemRepository.findById(saved.getId());

            // assert
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("findByUserIdAndProductId 메서드는")
    class FindByUserIdAndProductId {

        @Test
        void 존재하는_항목을_반환한다() {
            // arrange
            cartItemRepository.save(CartItem.of(1L, 100L, 3));

            // act
            Optional<CartItem> result = cartItemRepository.findByUserIdAndProductId(1L, 100L);

            // assert
            assertThat(result).isPresent();
        }

        @Test
        void 존재하지_않으면_empty를_반환한다() {
            // act
            Optional<CartItem> result = cartItemRepository.findByUserIdAndProductId(1L, 999L);

            // assert
            assertThat(result).isEmpty();
        }

        @Test
        void 소프트_삭제된_항목은_조회되지_않는다() {
            // arrange
            CartItem saved = cartItemRepository.save(CartItem.of(1L, 100L, 3));
            saved.remove();
            cartItemRepository.save(saved);

            // act
            Optional<CartItem> result = cartItemRepository.findByUserIdAndProductId(1L, 100L);

            // assert
            assertThat(result).isEmpty();
        }

        @Test
        void 소프트_삭제_후_동일_상품을_재추가할_수_있다() {
            // arrange
            CartItem saved = cartItemRepository.save(CartItem.of(1L, 100L, 3));
            saved.remove();
            CartItem deleted = cartItemRepository.save(saved);

            // act - 소프트 삭제된 항목을 restore하여 재추가
            CartItem found = cartItemRepository.findByUserIdAndProductIdIncludeDeleted(1L, 100L).orElseThrow();
            found.restore(5);
            CartItem restored = cartItemRepository.save(found);

            // assert
            Optional<CartItem> result = cartItemRepository.findByUserIdAndProductId(1L, 100L);
            assertThat(result).isPresent();
            assertThat(result.get().getQuantity()).isEqualTo(5);
            assertThat(result.get().getId()).isEqualTo(restored.getId());
        }
    }

    @Nested
    @DisplayName("findAllByUserId 메서드는")
    class FindAllByUserId {

        @Test
        void 사용자의_장바구니_항목을_반환한다() {
            // arrange
            cartItemRepository.save(CartItem.of(1L, 100L, 3));
            cartItemRepository.save(CartItem.of(1L, 200L, 1));

            // act
            List<CartItem> result = cartItemRepository.findAllByUserId(1L);

            // assert
            assertThat(result).hasSize(2);
        }

        @Test
        void 최근_추가순으로_반환한다() {
            // arrange
            cartItemRepository.save(CartItem.of(1L, 100L, 3));
            cartItemRepository.save(CartItem.of(1L, 200L, 1));

            // act
            List<CartItem> result = cartItemRepository.findAllByUserId(1L);

            // assert
            assertThat(result.get(0).getProductId()).isEqualTo(200L);
            assertThat(result.get(1).getProductId()).isEqualTo(100L);
        }

        @Test
        void 소프트_삭제된_항목은_제외된다() {
            // arrange
            CartItem item1 = cartItemRepository.save(CartItem.of(1L, 100L, 3));
            cartItemRepository.save(CartItem.of(1L, 200L, 1));
            item1.remove();
            cartItemRepository.save(item1);

            // act
            List<CartItem> result = cartItemRepository.findAllByUserId(1L);

            // assert
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getProductId()).isEqualTo(200L);
        }

        @Test
        void 다른_사용자의_항목은_포함되지_않는다() {
            // arrange
            cartItemRepository.save(CartItem.of(1L, 100L, 3));
            cartItemRepository.save(CartItem.of(2L, 200L, 1));

            // act
            List<CartItem> result = cartItemRepository.findAllByUserId(1L);

            // assert
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getProductId()).isEqualTo(100L);
        }
    }
}
