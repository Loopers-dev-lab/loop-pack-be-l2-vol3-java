package com.loopers.domain.like;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.product.Money;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.Stock;
import com.loopers.infrastructure.brand.BrandJpaRepository;
import com.loopers.infrastructure.like.LikeJpaRepository;
import com.loopers.infrastructure.product.ProductJpaRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
public class LikeServiceIntegrationTest {

    private static final Long USER_ID = 1L;
    private static final Long OTHER_USER_ID = 2L;
    private static final String PRODUCT_NAME = "나이키 에어맥스";
    private static final Money VALID_PRICE = new Money(10000);
    private static final Stock VALID_STOCK = new Stock(100);

    @Autowired
    private LikeService likeService;

    @Autowired
    private LikeJpaRepository likeJpaRepository;

    @Autowired
    private BrandJpaRepository brandJpaRepository;

    @Autowired
    private ProductJpaRepository productJpaRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("좋아요 등록 시")
    @Nested
    class Create {

        @DisplayName("중복되지 않은 좋아요 등록에 성공한다.")
        @Test
        void createLikeSucceed_whenNotDuplicated() {
            // arrange
            Brand brand = brandJpaRepository.save(new Brand("나이키"));
            Product product = productJpaRepository.save(
                    new Product(brand.getId(), PRODUCT_NAME, VALID_PRICE, VALID_STOCK));

            // act
            Like result = likeService.create(USER_ID, product.getId());

            // assert
            assertThat(result.getId()).isPositive();
            assertThat(result.getUserId()).isEqualTo(USER_ID);
            assertThat(result.getProductId()).isEqualTo(product.getId());

            // DB에 실제 저장 확인
            assertThat(likeJpaRepository.existsByUserIdAndProductId(USER_ID, product.getId())).isTrue();
        }

        @DisplayName("이미 좋아요한 상품에 다시 좋아요하면, CONFLICT 에러가 발생한다.")
        @Test
        void createLikeFail_whenAlreadyLiked() {
            // arrange
            Brand brand = brandJpaRepository.save(new Brand("나이키"));
            Product product = productJpaRepository.save(
                    new Product(brand.getId(), PRODUCT_NAME, VALID_PRICE, VALID_STOCK));
            likeJpaRepository.save(new Like(USER_ID, product.getId()));

            // act
            CoreException result = assertThrows(CoreException.class,
                    () -> likeService.create(USER_ID, product.getId()));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.CONFLICT);
        }
    }

    @DisplayName("좋아요 취소 시")
    @Nested
    class Delete {

        @DisplayName("존재하는 좋아요를 취소하면 hard delete된다.")
        @Test
        void deleteLikeSucceed_whenLikeExists() {
            // arrange
            Brand brand = brandJpaRepository.save(new Brand("나이키"));
            Product product = productJpaRepository.save(
                    new Product(brand.getId(), PRODUCT_NAME, VALID_PRICE, VALID_STOCK));
            likeJpaRepository.save(new Like(USER_ID, product.getId()));

            // act
            likeService.delete(USER_ID, product.getId());

            // assert - hard delete이므로 DB에서 완전히 삭제됨
            assertThat(likeJpaRepository.existsByUserIdAndProductId(USER_ID, product.getId())).isFalse();
        }

        @DisplayName("좋아요하지 않은 상품을 취소하면, NOT_FOUND 에러가 발생한다.")
        @Test
        void deleteLikeFail_whenLikeNotExists() {
            // arrange
            Brand brand = brandJpaRepository.save(new Brand("나이키"));
            Product product = productJpaRepository.save(
                    new Product(brand.getId(), PRODUCT_NAME, VALID_PRICE, VALID_STOCK));

            // act
            CoreException result = assertThrows(CoreException.class,
                    () -> likeService.delete(USER_ID, product.getId()));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("좋아요 목록 조회 시")
    @Nested
    class FindAllByUserId {

        @DisplayName("자신의 좋아요 목록만 반환한다.")
        @Test
        void returnsOnlyOwnLikes() {
            // arrange
            Brand brand = brandJpaRepository.save(new Brand("나이키"));
            Product product1 = productJpaRepository.save(
                    new Product(brand.getId(), PRODUCT_NAME, VALID_PRICE, VALID_STOCK));
            Product product2 = productJpaRepository.save(
                    new Product(brand.getId(), "나이키 조던", VALID_PRICE, VALID_STOCK));
            likeJpaRepository.save(new Like(USER_ID, product1.getId()));
            likeJpaRepository.save(new Like(USER_ID, product2.getId()));
            likeJpaRepository.save(new Like(OTHER_USER_ID, product1.getId()));

            // act
            List<Like> result = likeService.findAllByUserId(USER_ID);

            // assert
            assertThat(result).hasSize(2);
            assertThat(result).allMatch(like -> like.getUserId().equals(USER_ID));
        }

        @DisplayName("좋아요가 없으면 빈 목록을 반환한다.")
        @Test
        void returnsEmptyList_whenNoLikes() {
            // act
            List<Like> result = likeService.findAllByUserId(USER_ID);

            // assert
            assertThat(result).isEmpty();
        }
    }

    @DisplayName("상품 삭제 시 좋아요 cascade hard delete")
    @Nested
    class DeleteAllByProductId {

        @DisplayName("상품의 모든 좋아요가 hard delete된다.")
        @Test
        void deletesAllLikes_whenProductDeleted() {
            // arrange
            Brand brand = brandJpaRepository.save(new Brand("나이키"));
            Product product = productJpaRepository.save(
                    new Product(brand.getId(), PRODUCT_NAME, VALID_PRICE, VALID_STOCK));
            likeJpaRepository.save(new Like(USER_ID, product.getId()));
            likeJpaRepository.save(new Like(OTHER_USER_ID, product.getId()));

            // act
            likeService.deleteAllByProductId(product.getId());

            // assert
            assertThat(likeJpaRepository.existsByUserIdAndProductId(USER_ID, product.getId())).isFalse();
            assertThat(likeJpaRepository.existsByUserIdAndProductId(OTHER_USER_ID, product.getId())).isFalse();
        }
    }

    @DisplayName("브랜드 삭제 시 좋아요 cascade hard delete")
    @Nested
    class DeleteAllByProductIds {

        @DisplayName("여러 상품에 대한 좋아요가 모두 hard delete된다.")
        @Test
        void deletesAllLikes_whenMultipleProductsDeleted() {
            // arrange
            Brand brand = brandJpaRepository.save(new Brand("나이키"));
            Product product1 = productJpaRepository.save(
                    new Product(brand.getId(), PRODUCT_NAME, VALID_PRICE, VALID_STOCK));
            Product product2 = productJpaRepository.save(
                    new Product(brand.getId(), "나이키 조던", VALID_PRICE, VALID_STOCK));
            likeJpaRepository.save(new Like(USER_ID, product1.getId()));
            likeJpaRepository.save(new Like(USER_ID, product2.getId()));
            likeJpaRepository.save(new Like(OTHER_USER_ID, product1.getId()));

            // act
            likeService.deleteAllByProductIds(List.of(product1.getId(), product2.getId()));

            // assert
            assertThat(likeJpaRepository.findAllByUserIdOrderByCreatedAtDesc(USER_ID)).isEmpty();
            assertThat(likeJpaRepository.findAllByUserIdOrderByCreatedAtDesc(OTHER_USER_ID)).isEmpty();
        }

        @DisplayName("빈 ID 목록으로 호출하면 아무것도 삭제하지 않는다.")
        @Test
        void doesNothing_whenProductIdsIsEmpty() {
            // arrange
            Brand brand = brandJpaRepository.save(new Brand("나이키"));
            Product product = productJpaRepository.save(
                    new Product(brand.getId(), PRODUCT_NAME, VALID_PRICE, VALID_STOCK));
            likeJpaRepository.save(new Like(USER_ID, product.getId()));

            // act
            likeService.deleteAllByProductIds(List.of());

            // assert - 좋아요 삭제되지 않음
            assertThat(likeJpaRepository.existsByUserIdAndProductId(USER_ID, product.getId())).isTrue();
        }
    }
}
