package com.loopers.domain.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.loopers.support.error.CoreException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.context.ApplicationEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ProductLikeServiceTest {

    private ProductLikeService productLikeService;
    private FakeProductLikeRepository productLikeRepository;
    private List<Object> publishedEvents;

    @BeforeEach
    void setUp() {
        productLikeRepository = new FakeProductLikeRepository();
        publishedEvents = new ArrayList<>();
        ApplicationEventPublisher publisher = publishedEvents::add;
        productLikeService = new ProductLikeService(publisher, productLikeRepository);
    }

    @DisplayName("좋아요를 등록할 때, ")
    @Nested
    class Like {

        @DisplayName("유효한 userId와 productId가 주어지면, 좋아요가 저장된다.")
        @Test
        void like_whenValidValues() {
            // act
            productLikeService.like(1L, 2L);

            // assert
            assertThat(productLikeRepository.findByUserIdAndProductId(1L, 2L)).isPresent();
        }

        @DisplayName("이미 좋아요한 상품이면 CONFLICT 예외가 발생한다.")
        @Test
        void like_whenAlreadyLiked_throwsConflict() {
            // arrange
            productLikeService.like(1L, 2L);

            // act & assert
            assertThatThrownBy(() -> productLikeService.like(1L, 2L))
                .isInstanceOf(CoreException.class)
                .hasMessageContaining("이미 좋아요한 상품입니다");
        }
    }

    @DisplayName("좋아요를 취소할 때, ")
    @Nested
    class Unlike {

        @DisplayName("존재하는 좋아요가 주어지면, 좋아요가 삭제된다.")
        @Test
        void unlike_whenExists() {
            // arrange
            productLikeRepository.save(ProductLikeModel.create(1L, 2L));

            // act
            productLikeService.unlike(1L, 2L);

            // assert
            assertThat(productLikeRepository.findByUserIdAndProductId(1L, 2L)).isEmpty();
        }

        @DisplayName("좋아요 기록이 없으면 NOT_FOUND 예외가 발생한다.")
        @Test
        void unlike_whenNotExists_throwsNotFound() {
            // act & assert
            assertThatThrownBy(() -> productLikeService.unlike(1L, 2L))
                .isInstanceOf(CoreException.class)
                .hasMessageContaining("좋아요 기록이 없습니다");
        }
    }

    @DisplayName("좋아요 존재 여부를 확인할 때, ")
    @Nested
    class ExistsByUserIdAndProductId {

        @DisplayName("좋아요가 존재하면, true를 반환한다.")
        @Test
        void exists_whenLikeExists() {
            // arrange
            productLikeRepository.save(ProductLikeModel.create(1L, 2L));

            // act & assert
            assertThat(productLikeService.existsByUserIdAndProductId(1L, 2L)).isTrue();
        }

        @DisplayName("좋아요가 없으면, false를 반환한다.")
        @Test
        void exists_whenLikeNotExists() {
            // act & assert
            assertThat(productLikeService.existsByUserIdAndProductId(1L, 2L)).isFalse();
        }
    }

    @DisplayName("좋아요 목록을 조회할 때, ")
    @Nested
    class GetLikesByUserId {

        @DisplayName("사용자 ID로 조회하면, 해당 사용자의 좋아요 목록이 반환된다.")
        @Test
        void getLikesByUserId_whenLikesExist() {
            // arrange
            Long userId = 1L;
            productLikeRepository.save(ProductLikeModel.create(userId, 10L));
            productLikeRepository.save(ProductLikeModel.create(userId, 20L));
            productLikeRepository.save(ProductLikeModel.create(2L, 30L));

            // act
            List<ProductLikeModel> result = productLikeService.getLikesByUserId(userId);

            // assert
            assertThat(result).hasSize(2);
            assertThat(result).allMatch(like -> like.getUserId().equals(userId));
        }

        @DisplayName("좋아요가 없는 사용자 ID로 조회하면, 빈 목록이 반환된다.")
        @Test
        void getLikesByUserId_whenNoLikes() {
            // act
            List<ProductLikeModel> result = productLikeService.getLikesByUserId(999L);

            // assert
            assertThat(result).isEmpty();
        }
    }

    @DisplayName("좋아요 수를 조회할 때, ")
    @Nested
    class CountLikes {

        @DisplayName("상품의 좋아요 수를 반환한다.")
        @Test
        void countLikes_returnsCount() {
            // arrange
            productLikeRepository.save(ProductLikeModel.create(1L, 10L));
            productLikeRepository.save(ProductLikeModel.create(2L, 10L));
            productLikeRepository.save(ProductLikeModel.create(3L, 20L));

            // act & assert
            assertThat(productLikeService.countLikes(10L)).isEqualTo(2);
            assertThat(productLikeService.countLikes(20L)).isEqualTo(1);
            assertThat(productLikeService.countLikes(99L)).isEqualTo(0);
        }
    }

    @DisplayName("상품별 좋아요 수를 일괄 조회할 때, ")
    @Nested
    class CountLikesByProductIds {

        @DisplayName("여러 상품 ID로 조회하면, 상품별 좋아요 수 Map을 반환한다.")
        @Test
        void countLikesByProductIds_returnsCountMap() {
            // arrange
            productLikeRepository.save(ProductLikeModel.create(1L, 10L));
            productLikeRepository.save(ProductLikeModel.create(2L, 10L));
            productLikeRepository.save(ProductLikeModel.create(3L, 20L));

            // act
            Map<Long, Long> result = productLikeService.countLikesByProductIds(List.of(10L, 20L, 30L));

            // assert
            assertThat(result).containsEntry(10L, 2L);
            assertThat(result).containsEntry(20L, 1L);
            assertThat(result).containsEntry(30L, 0L);
        }
    }
}
