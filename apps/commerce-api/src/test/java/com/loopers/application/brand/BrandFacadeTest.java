package com.loopers.application.brand;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.vo.Price;
import com.loopers.domain.product.vo.Stock;
import com.loopers.fake.FakeBrandRepository;
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

class BrandFacadeTest {

    private BrandFacade brandFacade;
    private FakeBrandRepository brandRepository;
    private FakeProductRepository productRepository;

    @BeforeEach
    void setUp() {
        brandRepository = new FakeBrandRepository();
        productRepository = new FakeProductRepository();
        brandFacade = new BrandFacade(brandRepository, productRepository);
    }

    @Nested
    @DisplayName("브랜드 단건 조회")
    class GetBrand {

        @DisplayName("존재하는 브랜드를 조회하면 브랜드가 반환된다")
        @Test
        void getBrand_whenExists_returnsBrand() {
            // arrange
            Brand saved = brandRepository.save(new Brand("나이키", "스포츠 브랜드"));

            // act
            Brand result = brandFacade.getBrand(saved.getId());

            // assert
            assertThat(result.getId()).isEqualTo(saved.getId());
            assertThat(result.getName()).isEqualTo("나이키");
            assertThat(result.getDescription()).isEqualTo("스포츠 브랜드");
        }

        @DisplayName("존재하지 않는 브랜드를 조회하면 예외가 발생한다")
        @Test
        void getBrand_whenNotExists_throwsCoreException() {
            assertThatThrownBy(() -> brandFacade.getBrand(999L))
                    .isInstanceOf(CoreException.class)
                    .extracting(e -> ((CoreException) e).getErrorType())
                    .isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("브랜드 전체 조회")
    class GetAllBrands {

        @DisplayName("저장된 모든 브랜드가 반환된다")
        @Test
        void getAllBrands_returnsAll() {
            // arrange
            brandRepository.save(new Brand("나이키", "스포츠 브랜드"));
            brandRepository.save(new Brand("아디다스", "스포츠 브랜드"));

            // act
            List<Brand> result = brandFacade.getAllBrands();

            // assert
            assertThat(result).hasSize(2);
        }

        @DisplayName("저장된 브랜드가 없으면 빈 리스트가 반환된다")
        @Test
        void getAllBrands_whenEmpty_returnsEmptyList() {
            // act
            List<Brand> result = brandFacade.getAllBrands();

            // assert
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("브랜드 생성")
    class CreateBrand {

        @DisplayName("브랜드를 생성하면 ID가 부여되어 반환된다")
        @Test
        void createBrand_returnsWithId() {
            // act
            Brand result = brandFacade.createBrand("나이키", "스포츠 브랜드");

            // assert
            assertThat(result.getId()).isNotNull();
            assertThat(result.getId()).isGreaterThan(0L);
            assertThat(result.getName()).isEqualTo("나이키");
            assertThat(result.getDescription()).isEqualTo("스포츠 브랜드");
        }

        @DisplayName("생성된 브랜드가 저장소에 저장된다")
        @Test
        void createBrand_persistsInRepository() {
            // act
            Brand result = brandFacade.createBrand("나이키", "스포츠 브랜드");

            // assert
            assertThat(brandRepository.findById(result.getId())).isPresent();
        }
    }

    @Nested
    @DisplayName("브랜드 수정")
    class UpdateBrand {

        @DisplayName("존재하는 브랜드를 수정하면 변경된 정보가 반환된다")
        @Test
        void updateBrand_whenExists_returnsUpdated() {
            // arrange
            Brand saved = brandRepository.save(new Brand("나이키", "스포츠 브랜드"));

            // act
            Brand result = brandFacade.updateBrand(saved.getId(), "뉴나이키", "프리미엄 스포츠 브랜드");

            // assert
            assertThat(result.getName()).isEqualTo("뉴나이키");
            assertThat(result.getDescription()).isEqualTo("프리미엄 스포츠 브랜드");
        }

        @DisplayName("존재하지 않는 브랜드를 수정하면 예외가 발생한다")
        @Test
        void updateBrand_whenNotExists_throwsCoreException() {
            assertThatThrownBy(() -> brandFacade.updateBrand(999L, "뉴나이키", "설명"))
                    .isInstanceOf(CoreException.class)
                    .extracting(e -> ((CoreException) e).getErrorType())
                    .isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("브랜드 삭제")
    class DeleteBrand {

        @DisplayName("브랜드를 삭제하면 브랜드가 소프트 삭제된다")
        @Test
        void deleteBrand_softDeletesBrand() {
            // arrange
            Brand saved = brandRepository.save(new Brand("나이키", "스포츠 브랜드"));

            // act
            brandFacade.deleteBrand(saved.getId());

            // assert
            Brand deleted = brandRepository.findById(saved.getId()).orElseThrow();
            assertThat(deleted.getDeletedAt()).isNotNull();
        }

        @DisplayName("브랜드를 삭제하면 해당 브랜드의 상품도 소프트 삭제된다")
        @Test
        void deleteBrand_cascadeSoftDeletesProducts() {
            // arrange
            Brand brand = brandRepository.save(new Brand("나이키", "스포츠 브랜드"));
            Product product1 = productRepository.save(
                    new Product(brand.getId(), "에어맥스", new Price(150000), new Stock(10)));
            Product product2 = productRepository.save(
                    new Product(brand.getId(), "에어포스", new Price(120000), new Stock(20)));

            // act
            brandFacade.deleteBrand(brand.getId());

            // assert
            Product deletedProduct1 = productRepository.findById(product1.getId()).orElseThrow();
            Product deletedProduct2 = productRepository.findById(product2.getId()).orElseThrow();
            assertThat(deletedProduct1.getDeletedAt()).isNotNull();
            assertThat(deletedProduct2.getDeletedAt()).isNotNull();
        }

        @DisplayName("존재하지 않는 브랜드를 삭제하면 예외가 발생한다")
        @Test
        void deleteBrand_whenNotExists_throwsCoreException() {
            assertThatThrownBy(() -> brandFacade.deleteBrand(999L))
                    .isInstanceOf(CoreException.class)
                    .extracting(e -> ((CoreException) e).getErrorType())
                    .isEqualTo(ErrorType.NOT_FOUND);
        }

        @DisplayName("브랜드에 속한 상품이 없어도 삭제에 성공한다")
        @Test
        void deleteBrand_withNoProducts_succeeds() {
            // arrange
            Brand brand = brandRepository.save(new Brand("나이키", "스포츠 브랜드"));

            // act
            brandFacade.deleteBrand(brand.getId());

            // assert
            Brand deleted = brandRepository.findById(brand.getId()).orElseThrow();
            assertThat(deleted.getDeletedAt()).isNotNull();
        }
    }
}
