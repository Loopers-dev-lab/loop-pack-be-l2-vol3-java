package com.loopers.application.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.product.ImageType;
import com.loopers.domain.product.ProductImageService;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.product.event.ProductViewedEvent;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProductFacadeViewEventTest {

    @Mock ProductService productService;
    @Mock BrandService brandService;
    @Mock ProductImageService productImageService;
    @Mock ApplicationEventPublisher eventPublisher;
    ProductFacade productFacade;

    @BeforeEach
    void setUp() {
        productFacade = new ProductFacade(productService, brandService, productImageService, eventPublisher);

        ProductModel stubProduct = mock(ProductModel.class);
        when(stubProduct.getId()).thenReturn(1L);
        when(stubProduct.getBrandId()).thenReturn(10L);
        when(stubProduct.getName()).thenReturn("테스트 상품");
        when(stubProduct.getPrice()).thenReturn(50000);
        when(stubProduct.getStock()).thenReturn(100);
        when(productService.getById(1L)).thenReturn(stubProduct);

        BrandModel stubBrand = mock(BrandModel.class);
        when(stubBrand.getName()).thenReturn("테스트 브랜드");
        when(brandService.getById(10L)).thenReturn(stubBrand);

        when(productImageService.getImagesByProductIdAndType(1L, ImageType.MAIN)).thenReturn(List.of());
        when(productImageService.getImagesByProductIdAndType(1L, ImageType.DETAIL)).thenReturn(List.of());
    }

    @DisplayName("상품 상세를 조회할 때, ")
    @Nested
    class GetProductDetail {

        @DisplayName("회원 조회 시 userId가 이벤트에 포함된다.")
        @Test
        void getProductDetail_withUser_publishesEventWithUserId() {
            // act
            productFacade.getProductDetail(1L, 42L);

            // assert
            ArgumentCaptor<ProductViewedEvent> captor = ArgumentCaptor.forClass(ProductViewedEvent.class);
            verify(eventPublisher).publishEvent(captor.capture());
            assertThat(captor.getValue().userId()).isEqualTo(42L);
        }

        @DisplayName("비회원 조회 시 userId가 null로 이벤트에 포함된다.")
        @Test
        void getProductDetail_withoutUser_publishesEventWithNullUserId() {
            // act
            productFacade.getProductDetail(1L, null);

            // assert
            ArgumentCaptor<ProductViewedEvent> captor = ArgumentCaptor.forClass(ProductViewedEvent.class);
            verify(eventPublisher).publishEvent(captor.capture());
            assertThat(captor.getValue().userId()).isNull();
        }
    }
}
