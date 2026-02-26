package com.loopers.application.brand;

import com.loopers.domain.model.brand.Brand;
import com.loopers.domain.model.brand.BrandData;
import com.loopers.domain.model.brand.BrandName;
import com.loopers.domain.repository.BrandRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class BrandQueryServiceTest {

    private BrandRepository brandRepository;
    private BrandQueryService service;

    @BeforeEach
    void setUp() {
        brandRepository = mock(BrandRepository.class);
        service = new BrandQueryService(brandRepository);
    }

    @Nested
    @DisplayName("브랜드 조회")
    class QueryBrand {

        @Test
        @DisplayName("단건 조회 성공")
        void getBrand_success() {
            // given
            Brand brand = createBrand(1L, "나이키");
            when(brandRepository.findActiveById(1L)).thenReturn(Optional.of(brand));

            // when
            var result = service.getBrand(1L);

            // then
            assertThat(result.id()).isEqualTo(1L);
            assertThat(result.name()).isEqualTo("나이키");
        }

        @Test
        @DisplayName("존재하지 않는 브랜드 조회시 예외")
        void getBrand_fail_notFound() {
            // given
            when(brandRepository.findActiveById(999L)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> service.getBrand(999L))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("브랜드를 찾을 수 없습니다");
        }

        @Test
        @DisplayName("목록 조회 성공")
        void getBrands_success() {
            // given
            Brand brand1 = createBrand(1L, "나이키");
            Brand brand2 = createBrand(2L, "아디다스");

            when(brandRepository.findAllActive()).thenReturn(List.of(brand1, brand2));

            // when
            var result = service.getBrands();

            // then
            assertThat(result).hasSize(2);
            assertThat(result.get(0).name()).isEqualTo("나이키");
        }
    }

    private Brand createBrand(Long id, String name) {
        return Brand.reconstitute(new BrandData(id, BrandName.of(name), "설명",
                LocalDateTime.now(), LocalDateTime.now(), null));
    }
}
