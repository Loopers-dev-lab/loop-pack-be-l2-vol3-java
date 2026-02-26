package com.loopers.domain.brand;

import com.loopers.domain.product.ProductService;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BrandServiceTest {

    @Mock
    private BrandRepository brandRepository;

    @Mock
    private ProductService productService;

    @InjectMocks
    private BrandService brandService;

    @DisplayName("register 시")
    @Nested
    class Register {

        @DisplayName("유효한 이름이 주어지면, 저장 후 브랜드를 반환한다.")
        @Test
        void register_withValidName_shouldSaveAndReturn() {
            // given
            String name = "테스트 브랜드";
            BrandModel brand = BrandModel.create(name);
            when(brandRepository.save(any(BrandModel.class))).thenReturn(brand);

            // when
            BrandModel result = brandService.register(name);

            // then
            assertThat(result).isNotNull();
            assertThat(result.getName()).isEqualTo(name);
            verify(brandRepository).save(any(BrandModel.class));
        }

        @DisplayName("이름이 null이면, IllegalArgumentException이 발생한다.")
        @Test
        void register_withNullName_shouldThrow() {
            // when & then
            assertThrows(IllegalArgumentException.class, () -> brandService.register(null));
        }

        @DisplayName("이름이 빈 문자열이면, IllegalArgumentException이 발생한다.")
        @Test
        void register_withBlankName_shouldThrow() {
            // when & then
            assertThrows(IllegalArgumentException.class, () -> brandService.register(""));
        }
    }

    @DisplayName("findById 시")
    @Nested
    class FindById {

        @DisplayName("존재하는 ID면 Optional에 담아 반환한다.")
        @Test
        void findById_withExistingId_shouldReturnPresent() {
            // given
            Long id = 1L;
            BrandModel brand = BrandModel.create("브랜드");
            when(brandRepository.findById(id)).thenReturn(Optional.of(brand));

            // when
            Optional<BrandModel> result = brandService.findById(id);

            // then
            assertThat(result).isPresent();
            assertThat(result.get().getName()).isEqualTo("브랜드");
        }

        @DisplayName("존재하지 않는 ID면 empty를 반환한다.")
        @Test
        void findById_withNonExistentId_shouldReturnEmpty() {
            // given
            when(brandRepository.findById(999L)).thenReturn(Optional.empty());

            // when
            Optional<BrandModel> result = brandService.findById(999L);

            // then
            assertThat(result).isEmpty();
        }
    }

    @DisplayName("findByIdAndNotDeleted 시")
    @Nested
    class FindByIdAndNotDeleted {

        @DisplayName("미삭제 브랜드 ID면 Optional에 담아 반환한다.")
        @Test
        void findByIdAndNotDeleted_withExistingNotDeleted_shouldReturnPresent() {
            // given
            Long id = 1L;
            BrandModel brand = BrandModel.create("브랜드");
            when(brandRepository.findByIdAndNotDeleted(id)).thenReturn(Optional.of(brand));

            // when
            Optional<BrandModel> result = brandService.findByIdAndNotDeleted(id);

            // then
            assertThat(result).isPresent();
            assertThat(result.get().getName()).isEqualTo("브랜드");
        }

        @DisplayName("존재하지 않거나 삭제된 ID면 empty를 반환한다.")
        @Test
        void findByIdAndNotDeleted_withNonExistentOrDeleted_shouldReturnEmpty() {
            // given
            when(brandRepository.findByIdAndNotDeleted(999L)).thenReturn(Optional.empty());

            // when
            Optional<BrandModel> result = brandService.findByIdAndNotDeleted(999L);

            // then
            assertThat(result).isEmpty();
        }
    }

    @DisplayName("update 시")
    @Nested
    class Update {

        @DisplayName("존재하지 않는 ID면 NOT_FOUND 예외가 발생한다.")
        @Test
        void update_withNonExistentId_shouldThrowNotFound() {
            // given
            when(brandRepository.findByIdAndNotDeleted(999L)).thenReturn(Optional.empty());

            // when & then
            CoreException exception = assertThrows(CoreException.class, () -> {
                brandService.update(999L, "새 이름");
            });
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }

        @DisplayName("유효한 ID와 이름이 주어지면, 이름이 갱신된다.")
        @Test
        void update_withValidIdAndName_shouldUpdateAndSave() {
            // given
            Long id = 1L;
            BrandModel brand = BrandModel.create("기존 이름");
            when(brandRepository.findByIdAndNotDeleted(id)).thenReturn(Optional.of(brand));
            when(brandRepository.save(brand)).thenReturn(brand);

            // when
            BrandModel result = brandService.update(id, "새 이름");

            // then
            assertThat(result.getName()).isEqualTo("새 이름");
            verify(brandRepository).save(brand);
        }

        @DisplayName("이름이 null이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void update_withNullName_shouldThrowBadRequest() {
            // given
            Long id = 1L;
            BrandModel brand = BrandModel.create("브랜드");
            when(brandRepository.findByIdAndNotDeleted(id)).thenReturn(Optional.of(brand));

            // when & then
            CoreException exception = assertThrows(CoreException.class, () -> {
                brandService.update(id, null);
            });
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("delete 시")
    @Nested
    class Delete {

        @DisplayName("존재하지 않는 ID면 NOT_FOUND 예외가 발생한다.")
        @Test
        void delete_withNonExistentId_shouldThrowNotFound() {
            // given
            when(brandRepository.findById(999L)).thenReturn(Optional.empty());

            // when & then
            CoreException exception = assertThrows(CoreException.class, () -> {
                brandService.delete(999L);
            });
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }

        @DisplayName("존재하는 ID면 soft delete 후 저장한다.")
        @Test
        void delete_withExistingId_shouldDeleteAndSave() {
            // given
            Long id = 1L;
            BrandModel brand = BrandModel.create("브랜드");
            when(brandRepository.findById(id)).thenReturn(Optional.of(brand));
            when(brandRepository.save(brand)).thenReturn(brand);

            // when
            brandService.delete(id);

            // then: 연쇄 삭제 후 브랜드 삭제
            verify(productService).softDeleteByBrandId(eq(id));
            assertThat(brand.isDeleted()).isTrue();
            verify(brandRepository).save(brand);
        }
    }
}
