package com.loopers.application.brand;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.InMemoryBrandRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class BrandServiceTest {
    private InMemoryBrandRepository brandRepository;
    private BrandService brandService;

    @BeforeEach
    void setUp() {
        brandRepository = new InMemoryBrandRepository();
        brandService = new BrandService(brandRepository);
    }

    @DisplayName("브랜드 등록 시, ")
    @Nested
    class Register {
        @DisplayName("정상적으로 등록된다.")
        @Test
        void registersBrand() {
            // arrange
            String name = "나이키";
            String description = "스포츠 브랜드";

            // act
            Brand brand = brandService.register(name, description);

            // assert
            assertAll(
                () -> assertThat(brand.getName()).isEqualTo(name),
                () -> assertThat(brand.getDescription()).isEqualTo(description)
            );
        }
    }

    @DisplayName("브랜드 조회 시, ")
    @Nested
    class GetBrand {
        @DisplayName("존재하는 브랜드를 조회하면 정상 반환된다.")
        @Test
        void returnsBrand_whenExists() {
            // arrange
            Brand saved = brandService.register("나이키", "스포츠 브랜드");

            // act
            Brand found = brandService.getBrand(saved.getId());

            // assert
            assertThat(found.getName()).isEqualTo("나이키");
        }

        @DisplayName("존재하지 않는 브랜드를 조회하면 NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFoundException_whenNotExists() {
            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                brandService.getBrand(99999L);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }

        @DisplayName("삭제된 브랜드를 조회하면 NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFoundException_whenDeleted() {
            // arrange
            Brand saved = brandService.register("나이키", "스포츠 브랜드");
            brandService.delete(saved.getId());

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                brandService.getBrand(saved.getId());
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("브랜드 수정 시, ")
    @Nested
    class Update {
        @DisplayName("정상적으로 수정된다.")
        @Test
        void updatesBrand() {
            // arrange
            Brand saved = brandService.register("나이키", "스포츠 브랜드");

            // act
            Brand updated = brandService.update(saved.getId(), "new 나이키", "새로운 스포츠 브랜드");

            // assert
            assertAll(
                () -> assertThat(updated.getName()).isEqualTo("new 나이키"),
                () -> assertThat(updated.getDescription()).isEqualTo("새로운 스포츠 브랜드")
            );
        }

        @DisplayName("존재하지 않는 브랜드를 수정하면 NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFoundException_whenNotExists() {
            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                brandService.update(Long.MAX_VALUE, "아디다스", "독일 스포츠 브랜드");
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("브랜드 삭제 시, ")
    @Nested
    class Delete {
        @DisplayName("정상적으로 soft delete 된다.")
        @Test
        void deletesBrand() {
            // arrange
            Brand saved = brandService.register("나이키", "스포츠 브랜드");

            // act
            brandService.delete(saved.getId());

            // assert
            Brand deleted = brandRepository.findById(saved.getId()).orElseThrow();
            assertThat(deleted.getDeletedAt()).isNotNull();
        }

        @DisplayName("존재하지 않는 브랜드를 삭제하면 NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFoundException_whenNotExists() {
            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                brandService.delete(Long.MAX_VALUE);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }
}
