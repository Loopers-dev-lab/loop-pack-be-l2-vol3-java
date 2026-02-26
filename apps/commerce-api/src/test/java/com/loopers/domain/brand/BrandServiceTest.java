package com.loopers.domain.brand;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.instancio.Instancio;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.instancio.Select.field;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
@DisplayName("BrandService 단위 테스트")
class BrandServiceTest {

    @Mock
    private BrandRepository brandRepository;

    @InjectMocks
    private BrandService brandService;

    @Nested
    @DisplayName("브랜드 등록")
    class RegisterBrand {

        @Test
        @DisplayName("성공: 유효한 브랜드 정보로 등록")
        void registerBrand_Success() {
            // Given
            String name = "샤넬";
            String description = "프랑스 명품 브랜드";
            String logoUrl = "https://example.com/chanel.png";

            Brand savedBrand = Instancio.of(Brand.class)
                    .set(field(Brand::getName), name)
                    .set(field(Brand::getDescription), description)
                    .set(field(Brand::getLogoUrl), logoUrl)
                    .create();

            given(brandRepository.existsActiveByNameIgnoreCase(name)).willReturn(false);
            given(brandRepository.save(any(Brand.class))).willReturn(savedBrand);

            // When
            Brand result = brandService.register(name, description, logoUrl);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getName()).isEqualTo(name);
            assertThat(result.getDescription()).isEqualTo(description);
            assertThat(result.getLogoUrl()).isEqualTo(logoUrl);

            then(brandRepository).should().existsActiveByNameIgnoreCase(name);
            then(brandRepository).should().save(any(Brand.class));
        }

        @Test
        @DisplayName("성공: 앞뒤 공백 제거 후 등록")
        void registerBrand_TrimWhitespace() {
            // Given
            String nameWithSpaces = "  샤넬  ";
            String trimmedName = "샤넬";

            Brand savedBrand = Instancio.of(Brand.class)
                    .set(field(Brand::getName), trimmedName)
                    .create();

            given(brandRepository.existsActiveByNameIgnoreCase(trimmedName)).willReturn(false);
            given(brandRepository.save(any(Brand.class))).willReturn(savedBrand);

            // When
            Brand result = brandService.register(nameWithSpaces, null, null);

            // Then
            assertThat(result.getName()).isEqualTo(trimmedName);
            then(brandRepository).should().existsActiveByNameIgnoreCase(trimmedName);
        }

        @Test
        @DisplayName("실패: 브랜드명이 null")
        void registerBrand_NameIsNull() {
            // When & Then
            assertThatThrownBy(() -> brandService.register(null, null, null))
                    .isInstanceOf(CoreException.class)
                    .hasFieldOrPropertyWithValue("errorType", ErrorType.BAD_REQUEST)
                    .hasMessage("브랜드명은 필수입니다.");

            then(brandRepository).should(never()).existsActiveByNameIgnoreCase(anyString());
            then(brandRepository).should(never()).save(any(Brand.class));
        }

        @Test
        @DisplayName("실패: 브랜드명이 빈 문자열")
        void registerBrand_NameIsEmpty() {
            // When & Then
            assertThatThrownBy(() -> brandService.register("", null, null))
                    .isInstanceOf(CoreException.class)
                    .hasFieldOrPropertyWithValue("errorType", ErrorType.BAD_REQUEST)
                    .hasMessage("브랜드명은 필수입니다.");
        }

        @Test
        @DisplayName("실패: 브랜드명이 공백만")
        void registerBrand_NameIsBlank() {
            // When & Then
            assertThatThrownBy(() -> brandService.register("   ", null, null))
                    .isInstanceOf(CoreException.class)
                    .hasFieldOrPropertyWithValue("errorType", ErrorType.BAD_REQUEST)
                    .hasMessage("브랜드명은 필수입니다.");
        }

        @Test
        @DisplayName("실패: 브랜드명이 100자 초과")
        void registerBrand_NameTooLong() {
            // Given
            String longName = "a".repeat(101);

            // When & Then
            assertThatThrownBy(() -> brandService.register(longName, null, null))
                    .isInstanceOf(CoreException.class)
                    .hasFieldOrPropertyWithValue("errorType", ErrorType.BAD_REQUEST)
                    .hasMessage("브랜드명은 100자를 초과할 수 없습니다.");
        }

        @Test
        @DisplayName("실패: 브랜드명 중복 (대소문자 구분 없음)")
        void registerBrand_DuplicateName() {
            // Given
            String name = "샤넬";

            given(brandRepository.existsActiveByNameIgnoreCase(name)).willReturn(true);

            // When & Then
            assertThatThrownBy(() -> brandService.register(name, null, null))
                    .isInstanceOf(CoreException.class)
                    .hasFieldOrPropertyWithValue("errorType", ErrorType.CONFLICT)
                    .hasMessage("이미 존재하는 브랜드명입니다.");

            then(brandRepository).should().existsActiveByNameIgnoreCase(name);
            then(brandRepository).should(never()).save(any(Brand.class));
        }
    }

    @Nested
    @DisplayName("브랜드 조회")
    class GetBrand {

        @Test
        @DisplayName("성공: 유효한 브랜드 ID로 조회")
        void getBrand_Success() {
            // Given
            Long brandId = 1L;
            Brand brand = Instancio.of(Brand.class)
                    .set(field(Brand::getName), "샤넬")
                    .set(field(Brand::getDeletedAt), null)
                    .create();

            given(brandRepository.findActiveById(brandId)).willReturn(java.util.Optional.of(brand));

            // When
            Brand result = brandService.getBrand(brandId);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getName()).isEqualTo("샤넬");
            then(brandRepository).should().findActiveById(brandId);
        }

        @Test
        @DisplayName("실패: 존재하지 않는 브랜드 ID")
        void getBrand_NotFound() {
            // Given
            Long brandId = 999L;
            given(brandRepository.findActiveById(brandId)).willReturn(java.util.Optional.empty());

            // When & Then
            assertThatThrownBy(() -> brandService.getBrand(brandId))
                    .isInstanceOf(CoreException.class)
                    .hasFieldOrPropertyWithValue("errorType", ErrorType.NOT_FOUND)
                    .hasMessage("브랜드를 찾을 수 없습니다.");
        }

        @Test
        @DisplayName("실패: 삭제된 브랜드")
        void getBrand_Deleted() {
            // Given
            Long brandId = 1L;

            given(brandRepository.findActiveById(brandId)).willReturn(java.util.Optional.empty());

            // When & Then
            assertThatThrownBy(() -> brandService.getBrand(brandId))
                    .isInstanceOf(CoreException.class)
                    .hasFieldOrPropertyWithValue("errorType", ErrorType.NOT_FOUND)
                    .hasMessage("브랜드를 찾을 수 없습니다.");
        }
    }
}
