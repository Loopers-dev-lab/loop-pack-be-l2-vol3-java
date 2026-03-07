package com.loopers.domain.brand;

import com.loopers.support.error.BrandErrorType;
import com.loopers.support.error.CoreException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class BrandTest {

    @DisplayName("생성할 때,")
    @Nested
    class 생성 {

        @Test
        void 유효한_정보면_name과_description과_ACTIVE_상태로_생성된다() {
            // act
            Brand brand = Brand.register("나이키", "스포츠 브랜드");

            // assert
            assertThat(brand)
                    .extracting(Brand::getName, Brand::getDescription, Brand::getStatus)
                    .containsExactly("나이키", "스포츠 브랜드", BrandStatus.ACTIVE);
        }
    }

    @DisplayName("수정할 때,")
    @Nested
    class 수정 {

        @Test
        void 새로운_name과_description으로_변경된다() {
            // arrange
            Brand brand = Brand.register("나이키", "스포츠 브랜드");

            // act
            brand.changeInfo("아디다스", "독일 스포츠 브랜드");

            // assert
            assertThat(brand)
                    .extracting(Brand::getName, Brand::getDescription)
                    .containsExactly("아디다스", "독일 스포츠 브랜드");
        }
    }

    @DisplayName("상태를 변경할 때,")
    @Nested
    class 상태변경 {

        @Test
        void INACTIVE로_변경하면_status가_INACTIVE이다() {
            // arrange
            Brand brand = Brand.register("나이키", "스포츠 브랜드");

            // act
            brand.changeStatus(BrandStatus.INACTIVE);

            // assert
            assertThat(brand.getStatus()).isEqualTo(BrandStatus.INACTIVE);
        }

        @Test
        void ACTIVE로_변경하면_status가_ACTIVE이다() {
            // arrange
            Brand brand = Brand.register("나이키", "스포츠 브랜드");
            brand.changeStatus(BrandStatus.INACTIVE);

            // act
            brand.changeStatus(BrandStatus.ACTIVE);

            // assert
            assertThat(brand.getStatus()).isEqualTo(BrandStatus.ACTIVE);
        }
    }

    @DisplayName("삭제할 때,")
    @Nested
    class 삭제 {

        @Test
        void 이미_삭제된_브랜드를_재삭제하면_예외가_발생한다() {
            // arrange
            Brand brand = Brand.register("나이키", "스포츠 브랜드");
            brand.discontinue();

            // act & assert
            assertThatThrownBy(brand::discontinue)
                    .isInstanceOf(CoreException.class)
                    .extracting(e -> ((CoreException) e).getErrorType())
                    .isEqualTo(BrandErrorType.ALREADY_DELETED);
        }

        @Test
        void 삭제되지_않은_브랜드는_정상적으로_삭제된다() {
            // arrange
            Brand brand = Brand.register("나이키", "스포츠 브랜드");

            // act
            brand.discontinue();

            // assert
            assertThat(brand.getDeletedAt()).isNotNull();
        }
    }

    @DisplayName("활성 상태를 확인할 때,")
    @Nested
    class 활성상태확인 {

        @Test
        void INACTIVE_상태이면_false를_반환한다() {
            // arrange
            Brand brand = Brand.register("나이키", "스포츠 브랜드");
            brand.changeStatus(BrandStatus.INACTIVE);

            // act & assert
            assertThat(brand.isActive()).isFalse();
        }

        @Test
        void ACTIVE_상태이면_true를_반환한다() {
            // arrange
            Brand brand = Brand.register("나이키", "스포츠 브랜드");

            // act & assert
            assertThat(brand.isActive()).isTrue();
        }
    }

    @DisplayName("삭제 여부를 검증할 때,")
    @Nested
    class 삭제여부검증 {

        @Test
        void 삭제된_브랜드이면_예외가_발생한다() {
            // arrange
            Brand brand = Brand.register("나이키", "스포츠 브랜드");
            brand.discontinue();

            // act & assert
            assertThatThrownBy(brand::assertNotDeleted)
                    .isInstanceOf(CoreException.class)
                    .extracting(e -> ((CoreException) e).getErrorType())
                    .isEqualTo(BrandErrorType.ALREADY_DELETED);
        }

        @Test
        void 삭제되지_않은_브랜드이면_예외가_발생하지_않는다() {
            // arrange
            Brand brand = Brand.register("나이키", "스포츠 브랜드");

            // act & assert
            assertThatCode(brand::assertNotDeleted).doesNotThrowAnyException();
        }
    }
}
