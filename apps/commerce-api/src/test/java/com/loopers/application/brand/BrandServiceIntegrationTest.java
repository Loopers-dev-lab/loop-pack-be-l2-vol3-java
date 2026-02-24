package com.loopers.application.brand;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.loopers.application.like.LikeService;
import com.loopers.application.product.ProductCommand;
import com.loopers.support.BaseIntegrationTest;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.support.page.PageSize;

class BrandServiceIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private LikeService likeService;

    @DisplayName("브랜드를 생성할 때,")
    @Nested
    class createBrand {

        @DisplayName("유효한 정보를 입력하면, 브랜드가 DB에 저장된다.")
        @Test
        void savesBrandToDatabase_whenValidInputProvided() {
            // arrange
            var name = "brand name";
            var logoUrl = "logo url";
            var description = "brand description";

            // act
            var result = brandService.createBrand(name, logoUrl, description);

            // assert
            var savedBrand = brandService.getBrand(result.id());
            assertAll(
                    () -> assertThat(savedBrand.id()).isEqualTo(result.id()),
                    () -> assertThat(savedBrand.name()).isEqualTo(name),
                    () -> assertThat(savedBrand.logoUrl()).isEqualTo(logoUrl),
                    () -> assertThat(savedBrand.description()).isEqualTo(description)
            );
        }

        @DisplayName("이미 존재하는 브랜드 이름을 입력하면, ALREADY_EXISTS_BRAND_NAME 예외가 발생한다.")
        @Test
        void throwsException_whenDuplicateBrandNameProvided() {
            // arrange
            var name = "brand name";
            var logoUrl = "logo url";
            var description = "brand description";
            brandService.createBrand(name, logoUrl, description);

            // act & assert
            assertThatThrownBy(() -> brandService.createBrand(name, logoUrl, description))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.ALREADY_EXISTS_BRAND_NAME));
        }

        @DisplayName("삭제된 브랜드 이름으로 다시 생성할 수 있다.")
        @Test
        void allowsCreatingBrandWithDeletedName() {
            // arrange
            var name = "brand name";
            var logoUrl = "logo url";
            var description = "brand description";
            var brandResult = brandService.createBrand(name, logoUrl, description);
            brandService.deleteBrand(brandResult.id());

            // act
            var result = brandService.createBrand(name, logoUrl, description);

            // assert
            var savedBrand = brandService.getBrand(result.id());
            assertAll(
                    () -> assertThat(savedBrand.id()).isEqualTo(result.id()),
                    () -> assertThat(savedBrand.name()).isEqualTo(name),
                    () -> assertThat(savedBrand.logoUrl()).isEqualTo(logoUrl),
                    () -> assertThat(savedBrand.description()).isEqualTo(description)
            );
        }
    }

    @DisplayName("브랜드 목록을 조회할 때,")
    @Nested
    class GetBrands {

        @DisplayName("저장된 브랜드가 존재하면, 브랜드 목록이 반환된다.")
        @Test
        void returnsBrandList_whenBrandsExist() {
            // arrange
            var brand1 = brandService.createBrand("브랜드 1", "logo url 1", "브랜드 설명 1");
            var brand2 = brandService.createBrand("브랜드 2", "logo url 2", "브랜드 설명 2");

            // act
            var result = brandService.getBrands(new PageSize(0, 10));

            // assert
            assertAll(
                    () -> assertThat(result.content()).hasSize(2),
                    () -> assertThat(result.content()).extracting(BrandResult::id)
                            .containsExactly(brand2.id(), brand1.id()),
                    () -> assertThat(result.hasNext()).isFalse()
            );
        }

        @DisplayName("브랜드가 없으면, 빈 리스트가 반환된다.")
        @Test
        void returnsEmptyList_whenNoBrandsExist() {
            // act
            var result = brandService.getBrands(new PageSize(0, 10));

            // assert
            assertAll(
                    () -> assertThat(result.content()).isEmpty(),
                    () -> assertThat(result.hasNext()).isFalse()
            );
        }

        @DisplayName("페이지 크기보다 브랜드가 많으면, hasNext가 true이다.")
        @Test
        void returnsHasNextTrue_whenMoreBrandsExist() {
            // arrange
            brandService.createBrand("브랜드 1", "logo 1", "설명 1");
            brandService.createBrand("브랜드 2", "logo 2", "설명 2");
            brandService.createBrand("브랜드 3", "logo 3", "설명 3");

            // act
            var result = brandService.getBrands(new PageSize(0, 2));

            // assert
            assertAll(
                    () -> assertThat(result.content()).hasSize(2),
                    () -> assertThat(result.hasNext()).isTrue()
            );
        }

        @DisplayName("생성일 내림차순으로 정렬된다.")
        @Test
        void returnsBrandsSortedByCreatedAtDesc() {
            // arrange
            var brand1 = brandService.createBrand("브랜드 1", "logo 1", "설명 1");
            var brand2 = brandService.createBrand("브랜드 2", "logo 2", "설명 2");
            var brand3 = brandService.createBrand("브랜드 3", "logo 3", "설명 3");

            // act
            var result = brandService.getBrands(new PageSize(0, 10));

            // assert
            assertThat(result.content()).extracting(BrandResult::id)
                    .containsExactly(brand3.id(), brand2.id(), brand1.id());
        }

        @DisplayName("삭제된 브랜드도 조회 대상에 포함된다.")
        @Test
        void includesDeletedBrands() {
            // arrange
            var deletedBrand = brandService.createBrand("삭제 브랜드", "logo", "설명");
            brandService.deleteBrand(deletedBrand.id());

            var activeBrand = brandService.createBrand("활성 브랜드", "logo 2", "설명 2");

            // act
            var result = brandService.getBrands(new PageSize(0, 10));

            // assert
            assertAll(
                    () -> assertThat(result.content()).hasSize(2),
                    () -> assertThat(result.content()).extracting(BrandResult::id)
                            .containsExactly(activeBrand.id(), deletedBrand.id()),
                    () -> assertThat(result.content().get(0).deletedAt()).isNull(),
                    () -> assertThat(result.content().get(1).deletedAt()).isNotNull()
            );
        }
    }

    @DisplayName("브랜드를 조회할 때,")
    @Nested
    class GetBrand {

        @DisplayName("존재하는 브랜드 ID를 입력하면, 브랜드가 반환된다.")
        @Test
        void returnsBrand_whenExistingBrandIdProvided() {
            // arrange
            var created = brandService.createBrand("brand name", "logo url", "brand description");

            // act
            var result = brandService.getBrand(created.id());

            // assert
            assertAll(
                    () -> assertThat(result.id()).isEqualTo(created.id()),
                    () -> assertThat(result.name()).isEqualTo("brand name"),
                    () -> assertThat(result.logoUrl()).isEqualTo("logo url"),
                    () -> assertThat(result.description()).isEqualTo("brand description")
            );
        }

        @DisplayName("삭제된 브랜드 ID를 입력하면, 브랜드가 반환된다.")
        @Test
        void returnsBrand_whenDeletedBrandIdProvided() {
            // arrange
            var created = brandService.createBrand("brand name", "logo url", "brand description");
            brandService.deleteBrand(created.id());

            // act
            var result = brandService.getBrand(created.id());

            // assert
            assertAll(
                    () -> assertThat(result.id()).isEqualTo(created.id()),
                    () -> assertThat(result.name()).isEqualTo("brand name"),
                    () -> assertThat(result.logoUrl()).isEqualTo("logo url"),
                    () -> assertThat(result.description()).isEqualTo("brand description"),
                    () -> assertThat(result.deletedAt()).isNotNull()
            );
        }

        @DisplayName("존재하지 않는 브랜드 ID를 입력하면, BRAND_NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsException_whenNonExistingBrandIdProvided() {
            // act & assert
            assertThatThrownBy(() -> brandService.getBrand(999L))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.BRAND_NOT_FOUND));
        }
    }

    @DisplayName("활성 브랜드를 조회할 때,")
    @Nested
    class GetActiveBrand {

        @DisplayName("존재하는 활성 브랜드 ID를 입력하면, 브랜드가 반환된다.")
        @Test
        void returnsActiveBrand_whenExistingActiveBrandIdProvided() {
            // arrange
            var created = brandService.createBrand("brand name", "logo url", "brand description");

            // act
            var result = brandService.getActiveBrand(created.id());

            // assert
            assertAll(
                    () -> assertThat(result.id()).isEqualTo(created.id()),
                    () -> assertThat(result.name()).isEqualTo("brand name"),
                    () -> assertThat(result.logoUrl()).isEqualTo("logo url"),
                    () -> assertThat(result.description()).isEqualTo("brand description")
            );
        }

        @DisplayName("삭제된 브랜드 ID를 입력하면, BRAND_NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsException_whenDeletedBrandIdProvided() {
            // arrange
            var created = brandService.createBrand("brand name", "logo url", "brand description");
            brandService.deleteBrand(created.id());

            // act & assert
            assertThatThrownBy(() -> brandService.getActiveBrand(created.id()))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.BRAND_NOT_FOUND));
        }

        @DisplayName("존재하지 않는 브랜드 ID를 입력하면, BRAND_NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsException_whenNonExistingBrandIdProvided() {
            // act & assert
            assertThatThrownBy(() -> brandService.getActiveBrand(999L))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.BRAND_NOT_FOUND));
        }
    }

    @DisplayName("브랜드를 수정할 때,")
    @Nested
    class UpdateBrand {

        @DisplayName("존재하는 브랜드 ID와 유효한 정보를 입력하면, 브랜드가 수정된다.")
        @Test
        void updatesBrand_whenExistingBrandIdAndValidInputProvided() {
            // arrange
            var created = brandService.createBrand("brand name", "logo url", "brand description");
            var brandId = created.id();
            var newName = "new brand name";
            var newLogoUrl = "new logo url";
            var newDescription = "new brand description";

            // act
            brandService.updateBrand(brandId, newName, newLogoUrl, newDescription);

            // assert
            var updatedBrand = brandService.getBrand(brandId);
            assertAll(
                    () -> assertThat(updatedBrand.id()).isEqualTo(brandId),
                    () -> assertThat(updatedBrand.name()).isEqualTo(newName),
                    () -> assertThat(updatedBrand.logoUrl()).isEqualTo(newLogoUrl),
                    () -> assertThat(updatedBrand.description()).isEqualTo(newDescription)
            );
        }

        @DisplayName("존재하지 않는 브랜드 ID를 입력하면, BRAND_NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsException_whenBrandNotFound() {
            // act & assert
            assertThatThrownBy(() -> brandService.updateBrand(999L, "new name", "new logo", "new desc"))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.BRAND_NOT_FOUND));
        }

        @DisplayName("다른 활성 브랜드와 동일한 이름으로 수정하면, ALREADY_EXISTS_BRAND_NAME 예외가 발생한다.")
        @Test
        void throwsException_whenDuplicateNameExists() {
            // arrange
            brandService.createBrand("existing brand", "logo1", "desc1");
            var created = brandService.createBrand("my brand", "logo2", "desc2");

            // act & assert
            assertThatThrownBy(() -> brandService.updateBrand(created.id(), "existing brand", "logo2", "desc2"))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.ALREADY_EXISTS_BRAND_NAME));
        }

        @DisplayName("자기 자신과 동일한 이름으로 수정하면, 정상적으로 수정된다.")
        @Test
        void updatesBrand_whenKeepingSameName() {
            // arrange
            var created = brandService.createBrand("brand name", "logo url", "description");

            // act
            brandService.updateBrand(created.id(), "brand name", "new logo url", "new description");

            // assert
            var updatedBrand = brandService.getBrand(created.id());
            assertAll(
                    () -> assertThat(updatedBrand.name()).isEqualTo("brand name"),
                    () -> assertThat(updatedBrand.logoUrl()).isEqualTo("new logo url"),
                    () -> assertThat(updatedBrand.description()).isEqualTo("new description")
            );
        }

        @DisplayName("삭제된 브랜드와 동일한 이름으로 수정하면, 정상적으로 수정된다.")
        @Test
        void updatesBrand_whenNameMatchesDeletedBrand() {
            // arrange
            var deleted = brandService.createBrand("deleted brand", "logo1", "desc1");
            brandService.deleteBrand(deleted.id());

            var created = brandService.createBrand("my brand", "logo2", "desc2");

            // act
            brandService.updateBrand(created.id(), "deleted brand", "logo2", "desc2");

            // assert
            var updatedBrand = brandService.getBrand(created.id());
            assertThat(updatedBrand.name()).isEqualTo("deleted brand");
        }

        @DisplayName("삭제된 브랜드를 수정하면, ALREADY_DELETED_BRAND 예외가 발생한다.")
        @Test
        void throwsException_whenBrandIsDeleted() {
            // arrange
            var created = brandService.createBrand("brand name", "logo url", "description");
            brandService.deleteBrand(created.id());

            // act & assert
            assertThatThrownBy(() -> brandService.updateBrand(created.id(), "new name", "new logo", "new desc"))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.ALREADY_DELETED_BRAND));
        }
    }

    @DisplayName("브랜드를 삭제할 때,")
    @Nested
    class DeleteBrand {

        @DisplayName("유효한 브랜드이면, 브랜드가 삭제된다.")
        @Test
        void deletesBrand_whenBrandExists() {
            // arrange
            var created = brandService.createBrand("브랜드명", "logo url", "설명");

            // act
            brandService.deleteBrand(created.id());

            // assert
            var deleted = brandService.getBrand(created.id());
            assertThat(deleted.deletedAt()).isNotNull();
        }

        @DisplayName("이미 삭제된 브랜드이면, 아무 동작 없이 성공한다.")
        @Test
        void succeedsIdempotently_whenBrandIsAlreadyDeleted() {
            // arrange
            var created = brandService.createBrand("브랜드명", "logo url", "설명");
            brandService.deleteBrand(created.id());

            // act & assert
            assertThatCode(() -> brandService.deleteBrand(created.id()))
                    .doesNotThrowAnyException();
        }

        @DisplayName("소속 상품과 좋아요가 함께 삭제된다.")
        @Test
        void deletesProductsAndLikes_whenBrandDeleted() {
            // arrange
            var brandResult = brandService.createBrand("브랜드명", "logo url", "설명");
            var productId = productService.createProduct(
                    new ProductCommand.CreateProductCommand(brandResult.id(), "상품명", "thumb.png", 10000L, 100L, "설명")
            );
            var userId = 1L;
            likeService.likeProduct(userId, productId);

            // act
            brandService.deleteBrand(brandResult.id());

            // assert
            assertAll(
                    () -> assertThat(productService.getProduct(productId).deletedAt()).isNotNull(),
                    () -> assertThat(likeService.getLikedProducts(userId, new PageSize(0, 20)).content()).isEmpty()
            );
        }

        @DisplayName("존재하지 않는 브랜드이면, BRAND_NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsException_whenBrandNotFound() {
            // act & assert
            assertThatThrownBy(() -> brandService.deleteBrand(999L))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.BRAND_NOT_FOUND));
        }
    }
}
