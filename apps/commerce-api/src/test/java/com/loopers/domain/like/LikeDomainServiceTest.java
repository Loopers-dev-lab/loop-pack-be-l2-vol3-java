package com.loopers.domain.like;

import com.loopers.fake.FakeLikeRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LikeDomainServiceTest {

    private FakeLikeRepository fakeRepository;
    private LikeDomainService service;

    @BeforeEach
    void setUp() {
        fakeRepository = new FakeLikeRepository();
        service = new LikeDomainService(fakeRepository);
    }

    @DisplayName("좋아요를 등록할 때,")
    @Nested
    class LikeMethod {

        @DisplayName("새로운 좋아요면, 정상적으로 등록된다.")
        @Test
        void registersLike_whenLikeIsNew() {
            // arrange
            Long userId = 1L;
            Long productId = 100L;

            // act
            Like like = service.like(userId, productId);

            // assert
            assertThat(like.getUserId()).isEqualTo(userId);
            assertThat(like.getProductId()).isEqualTo(productId);
            assertThat(like.getId()).isNotNull();
            assertThat(fakeRepository.exists(userId, productId)).isTrue();
        }

        @DisplayName("이미 좋아요한 상품이면, CONFLICT 예외가 발생한다.")
        @Test
        void throwsConflictException_whenLikeAlreadyExists() {
            // arrange
            Long userId = 1L;
            Long productId = 100L;
            service.like(userId, productId);

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                service.like(userId, productId);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.CONFLICT);
        }

        @DisplayName("다른 사용자가 같은 상품에 좋아요하면, 정상적으로 등록된다.")
        @Test
        void registersLike_whenDifferentUserLikesSameProduct() {
            // arrange
            service.like(1L, 100L);

            // act
            Like like = service.like(2L, 100L);

            // assert
            assertThat(like.getUserId()).isEqualTo(2L);
            assertThat(fakeRepository.exists(2L, 100L)).isTrue();
        }

        @DisplayName("같은 사용자가 다른 상품에 좋아요하면, 정상적으로 등록된다.")
        @Test
        void registersLike_whenSameUserLikesDifferentProduct() {
            // arrange
            service.like(1L, 100L);

            // act
            Like like = service.like(1L, 200L);

            // assert
            assertThat(like.getProductId()).isEqualTo(200L);
            assertThat(fakeRepository.exists(1L, 200L)).isTrue();
        }
    }

    @DisplayName("좋아요를 취소할 때,")
    @Nested
    class Unlike {

        @DisplayName("존재하는 좋아요면, 정상적으로 삭제된다.")
        @Test
        void deletesLike_whenLikeExists() {
            // arrange
            Long userId = 1L;
            Long productId = 100L;
            service.like(userId, productId);
            assertThat(fakeRepository.exists(userId, productId)).isTrue();

            // act
            service.unlike(userId, productId);

            // assert
            assertThat(fakeRepository.exists(userId, productId)).isFalse();
        }

        @DisplayName("존재하지 않는 좋아요를 취소해도 예외가 발생하지 않는다. (멱등)")
        @Test
        void doesNotThrowException_whenLikeDoesNotExist() {
            // arrange
            Long userId = 1L;
            Long productId = 100L;

            // act & assert
            assertDoesNotThrow(() -> service.unlike(userId, productId));
        }

        @DisplayName("여러 번 취소해도 멱등하게 동작한다.")
        @Test
        void isIdempotent_whenUnlikingMultipleTimes() {
            // arrange
            service.like(1L, 100L);

            // act
            service.unlike(1L, 100L);
            service.unlike(1L, 100L);
            service.unlike(1L, 100L);

            // assert
            assertThat(fakeRepository.exists(1L, 100L)).isFalse();
        }
    }

    @DisplayName("좋아요 수를 조회할 때,")
    @Nested
    class CountByProductId {

        @DisplayName("상품에 좋아요가 있으면, 개수를 반환한다.")
        @Test
        void returnsCount_whenLikesExist() {
            // arrange
            Long productId = 100L;
            service.like(1L, productId);
            service.like(2L, productId);
            service.like(3L, productId);

            // act
            long count = service.countByProductId(productId);

            // assert
            assertThat(count).isEqualTo(3);
        }

        @DisplayName("상품에 좋아요가 없으면, 0을 반환한다.")
        @Test
        void returnsZero_whenNoLikesExist() {
            // act
            long count = service.countByProductId(100L);

            // assert
            assertThat(count).isEqualTo(0);
        }
    }

    @DisplayName("여러 상품의 좋아요 수를 조회할 때,")
    @Nested
    class CountByProductIds {

        @DisplayName("각 상품별 좋아요 수가 Map으로 반환된다.")
        @Test
        void returnsCountMap_forEachProduct() {
            // arrange
            service.like(1L, 100L);
            service.like(2L, 100L);
            service.like(1L, 200L);

            // act
            Map<Long, Long> counts = service.countByProductIds(List.of(100L, 200L, 300L));

            // assert
            assertThat(counts.get(100L)).isEqualTo(2);
            assertThat(counts.get(200L)).isEqualTo(1);
            assertThat(counts.get(300L)).isNull(); // 좋아요 없는 상품
        }
    }
}
