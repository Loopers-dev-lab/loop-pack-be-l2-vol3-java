package com.loopers.infrastructure.cart;

import com.loopers.domain.cart.CartItemModel;
import com.loopers.domain.cart.CartRepository;
import com.loopers.testcontainers.MySqlTestContainersConfig;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(MySqlTestContainersConfig.class)
class CartRepositoryIntegrationTest {

    private static final Long USER_ID = 1L;
    private static final Long OTHER_USER_ID = 2L;
    private static final Long PRODUCT_ID = 100L;
    private static final Long OPTION_ID = 10L;
    private static final int QUANTITY = 2;

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("save 시")
    @Nested
    class Save {

        @DisplayName("저장한 장바구니 항목을 조회할 수 있다.")
        @Test
        void save_shouldPersist() {
            // given
            CartItemModel item = CartItemModel.create(USER_ID, PRODUCT_ID, OPTION_ID, QUANTITY);

            // when
            CartItemModel saved = cartRepository.save(item);

            // then
            assertThat(saved.getId()).isNotNull();
            assertThat(saved.getUserId()).isEqualTo(USER_ID);
            assertThat(saved.getProductId()).isEqualTo(PRODUCT_ID);
            assertThat(saved.getOptionId()).isEqualTo(OPTION_ID);
            assertThat(saved.getQuantity()).isEqualTo(QUANTITY);
            Optional<CartItemModel> found = cartRepository.findByUserIdAndCartItemId(USER_ID, saved.getId());
            assertThat(found).isPresent();
            assertThat(found.get().getId()).isEqualTo(saved.getId());
        }
    }

    @DisplayName("findByUserId 시")
    @Nested
    class FindByUserId {

        @DisplayName("해당 사용자의 장바구니 항목 목록을 반환한다.")
        @Test
        void findByUserId_shouldReturnUserItems() {
            // given
            cartRepository.save(CartItemModel.create(USER_ID, PRODUCT_ID, OPTION_ID, QUANTITY));
            cartRepository.save(CartItemModel.create(USER_ID, PRODUCT_ID + 1, null, 1));

            // when
            List<CartItemModel> items = cartRepository.findByUserId(USER_ID);

            // then
            assertThat(items).hasSize(2);
        }

        @DisplayName("저장된 항목이 없으면 빈 목록을 반환한다.")
        @Test
        void findByUserId_whenNoItems_shouldReturnEmpty() {
            // given - 장바구니에 항목 없음
            // when
            List<CartItemModel> items = cartRepository.findByUserId(999L);

            // then
            assertThat(items).isEmpty();
        }
    }

    @DisplayName("findByUserIdAndCartItemId 시")
    @Nested
    class FindByUserIdAndCartItemId {

        @DisplayName("해당 사용자·항목이 있으면 Optional로 반환한다.")
        @Test
        void findByUserIdAndCartItemId_whenExists_shouldReturnPresent() {
            // given
            CartItemModel item = CartItemModel.create(USER_ID, PRODUCT_ID, OPTION_ID, QUANTITY);
            CartItemModel saved = cartRepository.save(item);

            // when
            Optional<CartItemModel> result = cartRepository.findByUserIdAndCartItemId(USER_ID, saved.getId());

            // then
            assertThat(result).isPresent();
            assertThat(result.get().getId()).isEqualTo(saved.getId());
        }

        @DisplayName("항목이 없으면 empty를 반환한다.")
        @Test
        void findByUserIdAndCartItemId_whenNotExists_shouldReturnEmpty() {
            // given - 존재하지 않는 cartItemId
            // when
            Optional<CartItemModel> result = cartRepository.findByUserIdAndCartItemId(USER_ID, 999_999L);

            // then
            assertThat(result).isEmpty();
        }

        @DisplayName("다른 사용자 소유 항목은 조회되지 않는다.")
        @Test
        void findByUserIdAndCartItemId_whenOtherUserItem_shouldReturnEmpty() {
            // given
            CartItemModel item = CartItemModel.create(USER_ID, PRODUCT_ID, OPTION_ID, QUANTITY);
            CartItemModel saved = cartRepository.save(item);

            // when
            Optional<CartItemModel> result = cartRepository.findByUserIdAndCartItemId(OTHER_USER_ID, saved.getId());

            // then
            assertThat(result).isEmpty();
        }
    }

    @DisplayName("delete 시")
    @Nested
    class Delete {

        @DisplayName("삭제 후 조회되지 않는다.")
        @Test
        void delete_shouldRemoveItem() {
            // given
            CartItemModel item = CartItemModel.create(USER_ID, PRODUCT_ID, OPTION_ID, QUANTITY);
            CartItemModel saved = cartRepository.save(item);

            // when
            cartRepository.delete(saved);

            // then
            assertThat(cartRepository.findByUserIdAndCartItemId(USER_ID, saved.getId())).isEmpty();
            assertThat(cartRepository.findByUserId(USER_ID)).isEmpty();
        }
    }
}
