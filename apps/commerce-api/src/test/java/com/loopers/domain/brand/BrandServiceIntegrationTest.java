package com.loopers.domain.brand;

import com.loopers.domain.brand.model.Brand;
import com.loopers.domain.brand.model.BrandCommand;
import com.loopers.domain.brand.service.BrandService;
import com.loopers.infrastructure.brand.entity.BrandEntity;
import com.loopers.infrastructure.brand.repository.BrandJpaRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.testcontainers.MySqlTestContainersConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
@Import(MySqlTestContainersConfig.class)
class BrandServiceIntegrationTest {

    @Autowired
    private BrandService brandService;

    @Autowired
    private BrandJpaRepository brandJpaRepository;

    @BeforeEach
    void setUp() {
        brandJpaRepository.deleteAll();
    }

    private BrandEntity saveBrand(String name, String description) {
        Brand brand = Brand.create(new BrandCommand.Create(name, description));
        return brandJpaRepository.save(BrandEntity.toEntity(brand));
    }

    @DisplayName("브랜드 생성")
    @Nested
    class CreateBrand {

        @DisplayName("정상적인 정보로 브랜드를 생성하면 DB에 저장된다")
        @Test
        void createBrand_success() {
            // arrange
            BrandCommand.Create command = new BrandCommand.Create("나이키", "스포츠 브랜드");

            // act
            Brand result = brandService.createBrand(command);

            // assert
            assertThat(result.getId()).isNotNull();
            BrandEntity saved = brandJpaRepository.findById(result.getId()).orElseThrow();
            assertThat(saved.toModel().getName().value()).isEqualTo("나이키");
            assertThat(saved.toModel().getDescription()).isEqualTo("스포츠 브랜드");
        }
    }

    @DisplayName("브랜드 조회")
    @Nested
    class FindBrand {

        @DisplayName("존재하지 않는 브랜드를 조회하면 NOT_FOUND 예외가 발생한다")
        @Test
        void findBrand_notFound() {
            // act & assert
            assertThatThrownBy(() -> brandService.findBrand(999L))
                .isInstanceOf(CoreException.class)
                .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.NOT_FOUND));
        }

        @DisplayName("존재하는 브랜드를 조회하면 브랜드 정보를 반환한다")
        @Test
        void findBrand_success() {
            // arrange
            BrandEntity saved = saveBrand("아디다스", "독일 스포츠 브랜드");

            // act
            Brand result = brandService.findBrand(saved.getId());

            // assert
            assertThat(result.getName().value()).isEqualTo("아디다스");
            assertThat(result.getDescription()).isEqualTo("독일 스포츠 브랜드");
        }
    }

    @DisplayName("브랜드 수정")
    @Nested
    class UpdateBrand {

        @DisplayName("존재하지 않는 브랜드를 수정하면 NOT_FOUND 예외가 발생한다")
        @Test
        void updateBrand_notFound() {
            // arrange
            BrandCommand.Update command = new BrandCommand.Update("수정브랜드", "수정설명");

            // act & assert
            assertThatThrownBy(() -> brandService.updateBrand(999L, command))
                .isInstanceOf(CoreException.class)
                .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.NOT_FOUND));
        }

        @DisplayName("존재하는 브랜드를 수정하면 DB에 반영된다")
        @Test
        void updateBrand_success() {
            // arrange
            BrandEntity saved = saveBrand("나이키", "스포츠 브랜드");
            BrandCommand.Update command = new BrandCommand.Update("나이키코리아", "한국 나이키");

            // act
            Brand result = brandService.updateBrand(saved.getId(), command);

            // assert
            assertThat(result.getName().value()).isEqualTo("나이키코리아");
            assertThat(result.getDescription()).isEqualTo("한국 나이키");
        }
    }

    @DisplayName("브랜드 삭제")
    @Nested
    class DeleteBrand {

        @DisplayName("존재하지 않는 브랜드를 삭제하면 NOT_FOUND 예외가 발생한다")
        @Test
        void deleteBrand_notFound() {
            // act & assert
            assertThatThrownBy(() -> brandService.deleteBrand(999L))
                .isInstanceOf(CoreException.class)
                .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.NOT_FOUND));
        }

        @DisplayName("존재하는 브랜드를 삭제하면 DB에서 제거된다")
        @Test
        void deleteBrand_success() {
            // arrange
            BrandEntity saved = saveBrand("삭제브랜드", "삭제될 브랜드");

            // act
            brandService.deleteBrand(saved.getId());

            // assert
            assertThat(brandJpaRepository.findById(saved.getId())).isEmpty();
        }
    }

    @DisplayName("브랜드 목록 조회")
    @Nested
    class FindBrandList {

        @DisplayName("브랜드가 없으면 빈 결과를 반환한다")
        @Test
        void findBrandList_empty() {
            // act
            Page<Brand> result = brandService.findBrandList(PageRequest.of(0, 10));

            // assert
            assertThat(result.getContent()).isEmpty();
            assertThat(result.getTotalElements()).isZero();
        }

        @DisplayName("여러 브랜드가 있으면 페이징 결과를 반환한다")
        @Test
        void findBrandList_paging() {
            // arrange
            saveBrand("브랜드A", "설명A");
            saveBrand("브랜드B", "설명B");
            saveBrand("브랜드C", "설명C");

            // act
            Page<Brand> firstPage = brandService.findBrandList(PageRequest.of(0, 2));
            Page<Brand> secondPage = brandService.findBrandList(PageRequest.of(1, 2));

            // assert
            assertThat(firstPage.getContent()).hasSize(2);
            assertThat(firstPage.getTotalElements()).isEqualTo(3);
            assertThat(firstPage.getTotalPages()).isEqualTo(2);
            assertThat(secondPage.getContent()).hasSize(1);
        }
    }
}
