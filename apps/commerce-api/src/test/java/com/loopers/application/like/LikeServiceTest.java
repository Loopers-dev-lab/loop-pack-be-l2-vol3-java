package com.loopers.application.like;

import com.loopers.domain.like.Like;
import com.loopers.domain.like.LikeRepository;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LikeServiceTest {

    private LikeService likeService;
    private FakeLikeRepository fakeLikeRepository;
    private FakeProductRepository fakeProductRepository;

    @BeforeEach
    void setUp() {
        fakeLikeRepository = new FakeLikeRepository();
        fakeProductRepository = new FakeProductRepository();
        likeService = new LikeService(fakeLikeRepository, fakeProductRepository);
    }

    @DisplayName("좋아요 등록")
    @Nested
    class LikeTests {

        @DisplayName("존재하는 상품에 좋아요를 등록하면 성공한다")
        @Test
        void success() {
            Long memberId = 1L;
            fakeProductRepository.save(new Product(1L, "상품", 10_000L, 10));
            Long productId = 1L;

            likeService.like(memberId, productId);

            assertThat(fakeLikeRepository.existsByMemberIdAndProductId(memberId, productId)).isTrue();
        }

        @DisplayName("이미 좋아요한 상품에 다시 좋아요해도 멱등하게 동작한다")
        @Test
        void idempotent() {
            Long memberId = 1L;
            fakeProductRepository.save(new Product(1L, "상품", 10_000L, 10));
            Long productId = 1L;

            likeService.like(memberId, productId);
            likeService.like(memberId, productId);

            assertThat(fakeLikeRepository.countByProductId(productId)).isEqualTo(1);
        }

        @DisplayName("존재하지 않는 상품에 좋아요하면 NOT_FOUND 예외가 발생한다")
        @Test
        void failsWhenProductNotFound() {
            Long memberId = 1L;
            Long nonExistentProductId = 999L;

            assertThatThrownBy(() -> likeService.like(memberId, nonExistentProductId))
                .isInstanceOf(CoreException.class)
                .hasFieldOrPropertyWithValue("errorType", ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("좋아요 취소")
    @Nested
    class Unlike {

        @DisplayName("좋아요한 상품의 좋아요를 취소하면 성공한다")
        @Test
        void success() {
            Long memberId = 1L;
            fakeProductRepository.save(new Product(1L, "상품", 10_000L, 10));
            Long productId = 1L;
            fakeLikeRepository.save(new com.loopers.domain.like.Like(memberId, productId));

            likeService.unlike(memberId, productId);

            assertThat(fakeLikeRepository.existsByMemberIdAndProductId(memberId, productId)).isFalse();
        }

        @DisplayName("좋아요하지 않은 상품의 좋아요를 취소해도 예외 없이 동작한다")
        @Test
        void idempotent() {
            Long memberId = 1L;
            Long productId = 100L;

            likeService.unlike(memberId, productId);

            assertThat(fakeLikeRepository.countByProductId(productId)).isEqualTo(0);
        }
    }

    static class FakeProductRepository implements ProductRepository {
        private final Map<Long, Product> store = new ConcurrentHashMap<>();
        private long nextId = 1;

        @Override
        public Product save(Product product) {
            Product toSave = new Product(
                product.getBrandId(),
                product.getName(),
                product.getPrice(),
                product.getStockQuantity()
            );
            long id = nextId++;
            store.put(id, toSave);
            return toSave;
        }

        @Override
        public Optional<Product> findById(Long id) {
            return Optional.ofNullable(store.get(id));
        }

        @Override
        public Optional<Product> findByIdForUpdate(Long id) {
            return findById(id);
        }

        @Override
        public java.util.List<Product> findAll(com.loopers.domain.product.SortCondition sort) {
            return new ArrayList<>(store.values());
        }
    }

    static class FakeLikeRepository implements LikeRepository {
        private final List<Like> store = new ArrayList<>();
        private long id = 1;

        @Override
        public Like save(Like like) {
            Like toSave = new Like(like.getMemberId(), like.getProductId());
            store.add(toSave);
            id++;
            return toSave;
        }

        @Override
        public void deleteByMemberIdAndProductId(Long memberId, Long productId) {
            store.removeIf(l -> l.getMemberId().equals(memberId) && l.getProductId().equals(productId));
        }

        @Override
        public boolean existsByMemberIdAndProductId(Long memberId, Long productId) {
            return store.stream()
                .anyMatch(l -> l.getMemberId().equals(memberId) && l.getProductId().equals(productId));
        }

        @Override
        public long countByProductId(Long productId) {
            return store.stream()
                .filter(l -> l.getProductId().equals(productId))
                .count();
        }

        @Override
        public Map<Long, Long> countByProductIds(List<Long> productIds) {
            return productIds.stream()
                .collect(java.util.stream.Collectors.toMap(id -> id, this::countByProductId));
        }
    }
}
