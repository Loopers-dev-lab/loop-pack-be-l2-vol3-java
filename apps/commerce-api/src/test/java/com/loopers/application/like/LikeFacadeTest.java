package com.loopers.application.like;

import com.loopers.domain.like.Like;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.vo.Price;
import com.loopers.domain.product.vo.Stock;
import com.loopers.fake.FakeLikeRepository;
import com.loopers.fake.FakeProductRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LikeFacadeTest {

    private LikeFacade likeFacade;
    private FakeLikeRepository likeRepository;
    private FakeProductRepository productRepository;

    @BeforeEach
    void setUp() {
        likeRepository = new FakeLikeRepository();
        productRepository = new FakeProductRepository();
        likeFacade = new LikeFacade(likeRepository, productRepository);
    }

    @Nested
    @DisplayName("좋아요 추가")
    class AddLike {

        @DisplayName("좋아요를 추가하면 Like 레코드가 저장되고 Product.likeCount가 1 증가한다")
        @Test
        void addLike_savesLikeRecord_andIncrementsLikeCount() {
            Product product = productRepository.save(
                    new Product(1L, "에어맥스", new Price(150000), new Stock(10)));
            Long memberId = 1L;

            likeFacade.addLike(memberId, product.getId());

            assertThat(likeRepository.existsByMemberIdAndProductId(memberId, product.getId())).isTrue();
            assertThat(likeRepository.countByProductId(product.getId())).isEqualTo(1);
            assertThat(product.getLikeCount()).isEqualTo(1);
        }

        @DisplayName("이미 좋아요한 상품에 다시 좋아요하면 멱등하게 처리된다 (likeCount 불변)")
        @Test
        void addLike_whenAlreadyLiked_isIdempotent() {
            Product product = productRepository.save(
                    new Product(1L, "에어맥스", new Price(150000), new Stock(10)));
            Long memberId = 1L;
            likeFacade.addLike(memberId, product.getId());

            likeFacade.addLike(memberId, product.getId());

            assertThat(likeRepository.countByProductId(product.getId())).isEqualTo(1);
            assertThat(likeRepository.findAllByMemberId(memberId)).hasSize(1);
            assertThat(product.getLikeCount()).isEqualTo(1);
        }

        @DisplayName("존재하지 않는 상품에 좋아요하면 예외가 발생한다")
        @Test
        void addLike_whenProductNotExists_throwsCoreException() {
            assertThatThrownBy(() -> likeFacade.addLike(1L, 999L))
                    .isInstanceOf(CoreException.class)
                    .extracting(e -> ((CoreException) e).getErrorType())
                    .isEqualTo(ErrorType.NOT_FOUND);
        }

        @DisplayName("여러 회원이 같은 상품에 좋아요하면 카운트가 누적된다")
        @Test
        void addLike_byMultipleMembers_accumulatesCount() {
            Product product = productRepository.save(
                    new Product(1L, "에어맥스", new Price(150000), new Stock(10)));

            likeFacade.addLike(1L, product.getId());
            likeFacade.addLike(2L, product.getId());
            likeFacade.addLike(3L, product.getId());

            assertThat(likeRepository.countByProductId(product.getId())).isEqualTo(3);
            assertThat(product.getLikeCount()).isEqualTo(3);
        }
    }

    @Nested
    @DisplayName("좋아요 취소")
    class RemoveLike {

        @DisplayName("좋아요를 취소하면 Like 레코드가 삭제되고 Product.likeCount가 1 감소한다")
        @Test
        void removeLike_deletesLikeRecord_andDecrementsLikeCount() {
            Product product = productRepository.save(
                    new Product(1L, "에어맥스", new Price(150000), new Stock(10)));
            Long memberId = 1L;
            likeFacade.addLike(memberId, product.getId());

            likeFacade.removeLike(memberId, product.getId());

            assertThat(likeRepository.existsByMemberIdAndProductId(memberId, product.getId())).isFalse();
            assertThat(likeRepository.countByProductId(product.getId())).isEqualTo(0);
            assertThat(product.getLikeCount()).isEqualTo(0);
        }

        @DisplayName("좋아요하지 않은 상품의 좋아요를 취소해도 예외 없이 멱등하게 처리된다")
        @Test
        void removeLike_whenNotLiked_isIdempotent() {
            Product product = productRepository.save(
                    new Product(1L, "에어맥스", new Price(150000), new Stock(10)));

            likeFacade.removeLike(1L, product.getId());

            assertThat(likeRepository.countByProductId(product.getId())).isEqualTo(0);
            assertThat(product.getLikeCount()).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("회원별 좋아요 목록 조회")
    class GetLikesByMemberId {

        @DisplayName("회원의 좋아요 목록이 반환된다")
        @Test
        void getLikesByMemberId_returnsLikes() {
            Product product1 = productRepository.save(
                    new Product(1L, "에어맥스", new Price(150000), new Stock(10)));
            Product product2 = productRepository.save(
                    new Product(1L, "에어포스", new Price(120000), new Stock(20)));
            Long memberId = 1L;
            likeFacade.addLike(memberId, product1.getId());
            likeFacade.addLike(memberId, product2.getId());

            List<Like> result = likeFacade.getLikesByMemberId(memberId);

            assertThat(result).hasSize(2);
        }

        @DisplayName("좋아요한 상품이 없으면 빈 리스트가 반환된다")
        @Test
        void getLikesByMemberId_whenEmpty_returnsEmptyList() {
            List<Like> result = likeFacade.getLikesByMemberId(1L);

            assertThat(result).isEmpty();
        }

        @DisplayName("다른 회원의 좋아요는 포함되지 않는다")
        @Test
        void getLikesByMemberId_excludesOtherMembers() {
            Product product = productRepository.save(
                    new Product(1L, "에어맥스", new Price(150000), new Stock(10)));
            likeFacade.addLike(1L, product.getId());
            likeFacade.addLike(2L, product.getId());

            List<Like> result = likeFacade.getLikesByMemberId(1L);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getMemberId()).isEqualTo(1L);
        }
    }
}
