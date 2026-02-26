package com.loopers.application.brand;

import com.loopers.domain.model.brand.Brand;
import com.loopers.domain.model.brand.BrandData;
import com.loopers.domain.model.brand.BrandName;
import com.loopers.domain.repository.BrandRepository;
import com.loopers.domain.model.common.DomainEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class BrandServiceTest {

    private BrandRepository brandRepository;
    private DomainEventPublisher eventPublisher;
    private BrandService service;

    @BeforeEach
    void setUp() {
        brandRepository = mock(BrandRepository.class);
        eventPublisher = mock(DomainEventPublisher.class);
        service = new BrandService(brandRepository, eventPublisher);
    }

    @Nested
    @DisplayName("브랜드 생성")
    class CreateBrand {

        @Test
        @DisplayName("브랜드 생성 성공")
        void createBrand_success() {
            // given
            when(brandRepository.existsByName(any(BrandName.class))).thenReturn(false);

            // when & then
            assertThatNoException()
                    .isThrownBy(() -> service.createBrand("나이키", "스포츠 브랜드"));

            verify(brandRepository).save(any(Brand.class));
        }

        @Test
        @DisplayName("중복 이름으로 생성시 예외")
        void createBrand_fail_duplicateName() {
            // given
            when(brandRepository.existsByName(any(BrandName.class))).thenReturn(true);

            // when & then
            assertThatThrownBy(() -> service.createBrand("나이키", "스포츠 브랜드"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("이미 존재하는 브랜드 이름");

            verify(brandRepository, never()).save(any(Brand.class));
        }
    }

    @Nested
    @DisplayName("브랜드 수정")
    class UpdateBrand {

        @Test
        @DisplayName("브랜드 수정 성공")
        void updateBrand_success() {
            // given
            Brand brand = createBrand(1L, "나이키");
            when(brandRepository.findActiveById(1L)).thenReturn(Optional.of(brand));

            // when & then
            assertThatNoException()
                    .isThrownBy(() -> service.updateBrand(1L, "아디다스", "변경된 설명"));

            verify(brandRepository).save(any(Brand.class));
        }

        @Test
        @DisplayName("존재하지 않는 브랜드 수정시 예외")
        void updateBrand_fail_notFound() {
            // given
            when(brandRepository.findActiveById(999L)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> service.updateBrand(999L, "아디다스", "설명"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("브랜드를 찾을 수 없습니다");
        }
    }

    @Nested
    @DisplayName("브랜드 삭제")
    class DeleteBrand {

        @Test
        @DisplayName("브랜드 삭제 성공 - 이벤트 발행")
        void deleteBrand_success_eventPublished() {
            // given
            Brand brand = createBrand(1L, "나이키");
            when(brandRepository.findActiveById(1L)).thenReturn(Optional.of(brand));

            // when
            service.deleteBrand(1L);

            // then
            verify(brandRepository).save(any(Brand.class));
            verify(eventPublisher).publishEvents(any(Brand.class));
        }

        @Test
        @DisplayName("존재하지 않는 브랜드 삭제시 예외")
        void deleteBrand_fail_notFound() {
            // given
            when(brandRepository.findActiveById(999L)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> service.deleteBrand(999L))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("브랜드를 찾을 수 없습니다");
        }
    }

    private Brand createBrand(Long id, String name) {
        return Brand.reconstitute(new BrandData(id, BrandName.of(name), "설명",
                LocalDateTime.now(), LocalDateTime.now(), null));
    }
}
