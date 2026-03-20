package com.loopers.infrastructure.cart;

import com.loopers.domain.cart.CartItemId;
import com.loopers.domain.cart.CartItemModel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(CartItemRepositoryImpl.class)
@ActiveProfiles("test")
@DisplayName("CartItemRepository 통합 테스트")
class CartItemRepositoryImplTest {

    @Autowired
    CartItemRepositoryImpl cartItemRepository;

    @Autowired
    TestEntityManager entityManager;

    @Test
    @DisplayName("장바구니 항목 저장")
    void save_ShouldPersist() {
        CartItemModel item = CartItemModel.create(1L, 1L, 2);

        CartItemModel saved = cartItemRepository.save(item);

        assertThat(saved.getUserId()).isEqualTo(1L);
        assertThat(saved.getProductId()).isEqualTo(1L);
        assertThat(saved.getQuantity()).isEqualTo(2);
    }

    @Test
    @DisplayName("복합 PK로 조회 - 존재하는 항목")
    void findById_Existing_ShouldReturn() {
        cartItemRepository.save(CartItemModel.create(1L, 1L, 2));

        Optional<CartItemModel> found = cartItemRepository.findById(new CartItemId(1L, 1L));

        assertThat(found).isPresent();
        assertThat(found.get().getQuantity()).isEqualTo(2);
    }

    @Test
    @DisplayName("복합 PK로 조회 - 존재하지 않는 항목")
    void findById_NotExisting_ShouldReturnEmpty() {
        Optional<CartItemModel> found = cartItemRepository.findById(new CartItemId(1L, 1L));

        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("장바구니 항목 삭제")
    void delete_ShouldRemove() {
        CartItemModel item = cartItemRepository.save(CartItemModel.create(1L, 1L, 2));

        cartItemRepository.delete(item);
        entityManager.flush();

        Optional<CartItemModel> found = cartItemRepository.findById(new CartItemId(1L, 1L));
        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("사용자별 장바구니 목록 조회")
    void findAllByUserId_ShouldReturnUserCart() {
        cartItemRepository.save(CartItemModel.create(1L, 1L, 1));
        cartItemRepository.save(CartItemModel.create(1L, 2L, 3));
        cartItemRepository.save(CartItemModel.create(2L, 1L, 2));

        List<CartItemModel> result = cartItemRepository.findAllByUserId(1L);

        assertThat(result).hasSize(2);
    }

    @Test
    @DisplayName("동일 복합 PK로 save 시 수량이 업데이트된다")
    void save_ExistingItem_ShouldUpdate() {
        CartItemModel item = cartItemRepository.save(CartItemModel.create(1L, 1L, 2));
        item.changeQuantity(5);

        cartItemRepository.save(item);
        entityManager.flush();
        entityManager.clear();

        Optional<CartItemModel> found = cartItemRepository.findById(new CartItemId(1L, 1L));
        assertThat(found).isPresent();
        assertThat(found.get().getQuantity()).isEqualTo(5);
    }
}
