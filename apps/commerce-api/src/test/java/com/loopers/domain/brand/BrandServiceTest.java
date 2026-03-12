package com.loopers.domain.brand;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.loopers.support.error.CoreException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

class BrandServiceTest {

    private BrandService brandService;
    private FakeBrandRepository brandRepository;

    @BeforeEach
    void setUp() {
        brandRepository = new FakeBrandRepository();
        brandService = new BrandService(brandRepository);
    }

    @DisplayName("브랜드를 등록할 때, ")
    @Nested
    class Register {

        @DisplayName("유효한 브랜드명이 주어지면, 정상적으로 등록된다.")
        @Test
        void register_whenValidName() {
            // act
            brandService.register("Nike");

            // assert
            assertThat(brandRepository.findByName("Nike")).isPresent();
        }

        @DisplayName("이미 존재하는 브랜드명이면 CONFLICT 예외가 발생한다.")
        @Test
        void register_whenDuplicateName() {
            // arrange
            brandService.register("Nike");

            // act & assert
            assertThatThrownBy(() -> brandService.register("Nike"))
                .isInstanceOf(CoreException.class)
                .satisfies(e -> assertThat(((CoreException) e).getErrorCode()).isEqualTo(BrandErrorCode.DUPLICATE_NAME));
        }
    }

    @DisplayName("브랜드를 조회할 때, ")
    @Nested
    class GetById {

        @DisplayName("존재하는 브랜드 ID가 주어지면, 정상적으로 조회된다.")
        @Test
        void getById_whenExists() {
            // arrange
            brandService.register("Nike");
            Long savedId = brandRepository.findByName("Nike").orElseThrow().getId();

            // act
            BrandModel found = brandService.getById(savedId);

            // assert
            assertThat(found.getName()).isEqualTo("Nike");
        }

        @DisplayName("존재하지 않는 브랜드 ID이면 NOT_FOUND 예외가 발생한다.")
        @Test
        void getById_whenNotExists() {
            assertThatThrownBy(() -> brandService.getById(999L))
                .isInstanceOf(CoreException.class)
                .satisfies(e -> assertThat(((CoreException) e).getErrorCode()).isEqualTo(BrandErrorCode.NOT_FOUND));
        }

        @DisplayName("삭제된 브랜드 ID이면 NOT_FOUND 예외가 발생한다.")
        @Test
        void getById_whenDeleted() {
            // arrange
            brandService.register("Nike");
            Long savedId = brandRepository.findByName("Nike").orElseThrow().getId();
            brandService.delete(savedId);

            // act & assert
            assertThatThrownBy(() -> brandService.getById(savedId))
                .isInstanceOf(CoreException.class)
                .satisfies(e -> assertThat(((CoreException) e).getErrorCode()).isEqualTo(BrandErrorCode.NOT_FOUND));
        }
    }

    @DisplayName("브랜드를 수정할 때, ")
    @Nested
    class Update {

        @DisplayName("유효한 브랜드명이 주어지면, 정상적으로 수정된다.")
        @Test
        void update_whenValidName() {
            // arrange
            brandService.register("Nike");
            Long savedId = brandRepository.findByName("Nike").orElseThrow().getId();

            // act
            brandService.update(savedId, "Adidas");

            // assert
            BrandModel updated = brandService.getById(savedId);
            assertThat(updated.getName()).isEqualTo("Adidas");
        }

        @DisplayName("다른 브랜드가 사용 중인 이름이면 CONFLICT 예외가 발생한다.")
        @Test
        void update_whenDuplicateName() {
            // arrange
            brandService.register("Nike");
            brandService.register("Adidas");
            Long adidasId = brandRepository.findByName("Adidas").orElseThrow().getId();

            // act & assert
            assertThatThrownBy(() -> brandService.update(adidasId, "Nike"))
                .isInstanceOf(CoreException.class)
                .satisfies(e -> assertThat(((CoreException) e).getErrorCode()).isEqualTo(BrandErrorCode.DUPLICATE_NAME));
        }
    }

    @DisplayName("브랜드를 삭제할 때, ")
    @Nested
    class Delete {

        @DisplayName("존재하는 브랜드 ID가 주어지면, 정상적으로 삭제된다.")
        @Test
        void delete_whenExists() {
            // arrange
            brandService.register("Nike");
            Long savedId = brandRepository.findByName("Nike").orElseThrow().getId();

            // act
            brandService.delete(savedId);

            // assert
            assertThatThrownBy(() -> brandService.getById(savedId))
                .isInstanceOf(CoreException.class)
                .satisfies(e -> assertThat(((CoreException) e).getErrorCode()).isEqualTo(BrandErrorCode.NOT_FOUND));
        }
    }

    @DisplayName("브랜드 목록을 조회할 때, ")
    @Nested
    class GetAll {

        @DisplayName("등록된 브랜드가 있으면, 페이지네이션된 목록을 반환한다.")
        @Test
        void getAll_whenBrandsExist() {
            // arrange
            brandService.register("Nike");
            brandService.register("Adidas");
            brandService.register("Puma");

            // act
            Page<BrandModel> result = brandService.getAll(PageRequest.of(0, 2));

            // assert
            assertThat(result.getContent()).hasSize(2);
            assertThat(result.getTotalElements()).isEqualTo(3);
            assertThat(result.getTotalPages()).isEqualTo(2);
        }

        @DisplayName("등록된 브랜드가 없으면, 빈 목록을 반환한다.")
        @Test
        void getAll_whenNoBrandsExist() {
            // act
            Page<BrandModel> result = brandService.getAll(PageRequest.of(0, 20));

            // assert
            assertThat(result.getContent()).isEmpty();
            assertThat(result.getTotalElements()).isEqualTo(0);
        }

        @DisplayName("삭제된 브랜드는 목록에 포함되지 않는다.")
        @Test
        void getAll_excludesDeletedBrands() {
            // arrange
            brandService.register("Nike");
            brandService.register("Adidas");
            Long nikeId = brandRepository.findByName("Nike").orElseThrow().getId();
            brandService.delete(nikeId);

            // act
            Page<BrandModel> result = brandService.getAll(PageRequest.of(0, 20));

            // assert
            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getName()).isEqualTo("Adidas");
        }
    }
}
