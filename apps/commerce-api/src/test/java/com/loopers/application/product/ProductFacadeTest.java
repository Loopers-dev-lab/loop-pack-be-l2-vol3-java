package com.loopers.application.product;

import com.loopers.application.observability.ProductViewOutboxRecorder;
import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.like.LikeService;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.product.ProductSortOrder;
import com.loopers.domain.product.Money;
import com.loopers.domain.product.StockQuantity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductFacadeTest {

    private static final Long PRODUCT_ID = 1L;
    private static final Long BRAND_ID = 10L;
    private static final String BRAND_NAME = "테스트브랜드";
    private static final String PRODUCT_NAME = "테스트상품";
    private static final BigDecimal PRICE = new BigDecimal("15000");
    private static final int STOCK_QUANTITY = 5;
    private static final long LIKE_COUNT = 3L;

    @Mock
    private ProductService productService;
    @Mock
    private BrandService brandService;
    @Mock
    private LikeService likeService;
    @Mock
    private ProductCacheService productCacheService;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private ProductViewOutboxRecorder productViewOutboxRecorder;

    @InjectMocks
    private ProductFacade productFacade;

    @DisplayName("getProductDetail 시")
    @Nested
    class GetProductDetail {

        @Test
        @DisplayName("상품이 없으면 empty를 반환한다.")
        void getProductDetail_whenProductNotFound_shouldReturnEmpty() {
            when(productCacheService.getDetail(PRODUCT_ID)).thenReturn(Optional.empty());
            when(productService.findByIdAndNotDeleted(PRODUCT_ID)).thenReturn(Optional.empty());

            Optional<ProductDetailInfo> result = productFacade.getProductDetail(PRODUCT_ID);

            assertThat(result).isEmpty();
            verify(productService).findByIdAndNotDeleted(PRODUCT_ID);
            verify(productViewOutboxRecorder, never()).recordProductViewed(PRODUCT_ID);
        }

        @Test
        @DisplayName("브랜드가 없거나 삭제되었으면 empty를 반환한다.")
        void getProductDetail_whenBrandNotFound_shouldReturnEmpty() {
            when(productCacheService.getDetail(PRODUCT_ID)).thenReturn(Optional.empty());
            ProductModel product = ProductModel.create(BRAND_ID, PRODUCT_NAME, Money.of(PRICE),
                    StockQuantity.of(STOCK_QUANTITY));
            when(productService.findByIdAndNotDeleted(PRODUCT_ID)).thenReturn(Optional.of(product));
            when(brandService.findByIdAndNotDeleted(BRAND_ID)).thenReturn(Optional.empty());

            Optional<ProductDetailInfo> result = productFacade.getProductDetail(PRODUCT_ID);

            assertThat(result).isEmpty();
            verify(productService).findByIdAndNotDeleted(PRODUCT_ID);
            verify(brandService).findByIdAndNotDeleted(BRAND_ID);
            verify(productViewOutboxRecorder, never()).recordProductViewed(PRODUCT_ID);
        }

        @Test
        @DisplayName("상품·브랜드가 있으면 ProductDetailInfo에 브랜드명·좋아요 수를 포함해 반환한다.")
        void getProductDetail_whenValid_shouldReturnProductDetailInfoWithBrandAndLikeCount() {
            when(productCacheService.getDetail(PRODUCT_ID)).thenReturn(Optional.empty());
            ProductModel product = ProductModel.create(BRAND_ID, PRODUCT_NAME, Money.of(PRICE),
                    StockQuantity.of(STOCK_QUANTITY));
            BrandModel brand = BrandModel.create(BRAND_NAME);
            when(productService.findByIdAndNotDeleted(PRODUCT_ID)).thenReturn(Optional.of(product));
            when(brandService.findByIdAndNotDeleted(BRAND_ID)).thenReturn(Optional.of(brand));
            when(likeService.getLikeCountFromStats(PRODUCT_ID)).thenReturn(LIKE_COUNT);

            Optional<ProductDetailInfo> result = productFacade.getProductDetail(PRODUCT_ID);

            assertThat(result).isPresent();
            ProductDetailInfo info = result.get();
            assertThat(info.brandId()).isEqualTo(BRAND_ID);
            assertThat(info.brandName()).isEqualTo(BRAND_NAME);
            assertThat(info.name()).isEqualTo(PRODUCT_NAME);
            assertThat(info.price()).isEqualTo(PRICE);
            assertThat(info.stockQuantity()).isEqualTo(STOCK_QUANTITY);
            assertThat(info.likeCount()).isEqualTo(LIKE_COUNT);
            verify(productService).findByIdAndNotDeleted(PRODUCT_ID);
            verify(brandService).findByIdAndNotDeleted(BRAND_ID);
            verify(likeService).getLikeCountFromStats(PRODUCT_ID);
            verify(productViewOutboxRecorder).recordProductViewed(PRODUCT_ID);
        }

        @Test
        @DisplayName("캐시 히트 시에도 상품 조회 Outbox를 기록한다.")
        void getProductDetail_whenCached_shouldRecordViewOutbox() {
            ProductDetailInfo cached = new ProductDetailInfo(
                    PRODUCT_ID, BRAND_ID, BRAND_NAME, PRODUCT_NAME, PRICE, STOCK_QUANTITY, LIKE_COUNT);
            when(productCacheService.getDetail(PRODUCT_ID)).thenReturn(Optional.of(cached));

            Optional<ProductDetailInfo> result = productFacade.getProductDetail(PRODUCT_ID);

            assertThat(result).contains(cached);
            verify(productService, never()).findByIdAndNotDeleted(PRODUCT_ID);
            verify(productViewOutboxRecorder).recordProductViewed(PRODUCT_ID);
        }
    }

    @DisplayName("getProductList 시")
    @Nested
    class GetProductList {

        @Test
        @DisplayName("정렬·페이징·브랜드 필터로 목록을 반환하고, likeCount를 채운다.")
        void getProductList_shouldReturnPagedListWithBrandAndLikeCount() {
            when(productCacheService.getList(null, "latest", 20)).thenReturn(Optional.empty());
            ProductModel product = ProductModel.create(BRAND_ID, PRODUCT_NAME, Money.of(PRICE),
                    StockQuantity.of(STOCK_QUANTITY));
            BrandModel brand = BrandModel.create(BRAND_NAME);
            Pageable pageable = PageRequest.of(0, 20);
            when(productService.findNotDeletedForList(ProductSortOrder.LATEST, null, 0, 20))
                    .thenReturn(new PageImpl<>(List.of(product), pageable, 1));
            when(brandService.findByIdAndNotDeletedIn(List.of(BRAND_ID))).thenReturn(Map.of(BRAND_ID, brand));
            when(likeService.getLikeCountByProductIdsFromStats(List.of(product.getId())))
                    .thenReturn(Map.of(product.getId(), LIKE_COUNT));

            var result = productFacade.getProductList(null, "latest", 0, 20);

            assertThat(result.getContent()).hasSize(1);
            ProductListItemInfo item = result.getContent().get(0);
            assertThat(item.id()).isEqualTo(product.getId());
            assertThat(item.name()).isEqualTo(PRODUCT_NAME);
            assertThat(item.brandName()).isEqualTo(BRAND_NAME);
            assertThat(item.likeCount()).isEqualTo(LIKE_COUNT);
            assertThat(result.getTotalElements()).isEqualTo(1);
            verify(brandService).findByIdAndNotDeletedIn(List.of(BRAND_ID));
        }

        @Test
        @DisplayName("brandId가 있으면 해당 브랜드만 조회한다.")
        void getProductList_withBrandId_shouldFilterByBrand() {
            when(productCacheService.getList(BRAND_ID, "latest", 20)).thenReturn(Optional.empty());
            when(productService.findNotDeletedForList(ProductSortOrder.LATEST, BRAND_ID, 0, 20))
                    .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

            var result = productFacade.getProductList(BRAND_ID, "latest", 0, 20);

            assertThat(result.getContent()).isEmpty();
            verify(productService).findNotDeletedForList(ProductSortOrder.LATEST, BRAND_ID, 0, 20);
        }
    }
}
