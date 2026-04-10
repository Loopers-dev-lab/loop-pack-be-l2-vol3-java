package com.loopers.application.product;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandService;
import com.loopers.application.product.ProductInfo;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.product.ProductStockModel;
import com.loopers.domain.product.StockService;
import com.loopers.support.page.PagedResult;
import com.loopers.support.enums.ProductSortType;
import com.loopers.support.page.PageQuery;
import com.loopers.support.page.PagedResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProductFacade 단위 테스트")
class ProductFacadeTest {

    @Mock
    ProductService productService;

    @Mock
    StockService stockService;

    @Mock
    BrandService brandService;

    @Mock
    ApplicationEventPublisher eventPublisher;

    @InjectMocks
    ProductFacade productFacade;

    @Test
    @DisplayName("고객용 상품 목록 LATEST 정렬 시 페이징된 결과에 브랜드명, 좋아요 수가 포함된다")
    void getProductsForCustomer_WithLatestSort_ShouldReturnPagedResult() {
        ProductModel product1 = mock(ProductModel.class);
        ProductModel product2 = mock(ProductModel.class);
        when(product1.getProductId()).thenReturn(1L);
        when(product1.getBrandId()).thenReturn(1L);
        when(product1.getLikeCount()).thenReturn(5L);
        when(product2.getProductId()).thenReturn(2L);
        when(product2.getBrandId()).thenReturn(1L);
        when(product2.getLikeCount()).thenReturn(3L);
        ProductStockModel stock1 = mock(ProductStockModel.class);
        ProductStockModel stock2 = mock(ProductStockModel.class);
        when(stock1.getAvailableQty()).thenReturn(50);
        when(stock2.getAvailableQty()).thenReturn(30);

        BrandModel brand = mock(BrandModel.class);
        when(brand.getBrandId()).thenReturn(1L);
        when(brand.getBrandName()).thenReturn("테스트브랜드");

        when(stock1.getProductId()).thenReturn(1L);
        when(stock2.getProductId()).thenReturn(2L);

        // keyword가 있으므로 캐시를 거치지 않고 DB 직접 조회
        PagedResult<ProductModel> pagedResult = new PagedResult<>(List.of(product1, product2), 0, 20, 2, 1);
        when(productService.findAllForCustomer(eq("검색어"), eq((Long) null), any(PageQuery.class)))
                .thenReturn(pagedResult);
        when(stockService.findAllByProductIds(List.of(1L, 2L))).thenReturn(List.of(stock1, stock2));
        when(brandService.findAllByIds(List.of(1L))).thenReturn(List.of(brand));

        PagedResult<ProductInfo> result = productFacade.getProductsForCustomer(
                "검색어", null, ProductSortType.LATEST, 0, 20);

        assertThat(result.content()).hasSize(2);
        assertThat(result.content().get(0).getBrandName()).isEqualTo("테스트브랜드");
        assertThat(result.content().get(0).getLikeCount()).isEqualTo(5L);
        assertThat(result.content().get(1).getLikeCount()).isEqualTo(3L);
    }

    @Test
    @DisplayName("고객용 상품 목록 LIKES_DESC 정렬 시 likeCount DESC PageQuery로 DB 정렬을 요청한다")
    void getProductsForCustomer_WithLikesDescSort_ShouldRequestDbSortByLikeCount() {
        ProductModel product1 = mock(ProductModel.class);
        ProductModel product2 = mock(ProductModel.class);
        when(product1.getProductId()).thenReturn(1L);
        when(product1.getBrandId()).thenReturn(1L);
        when(product1.getLikeCount()).thenReturn(10L);
        when(product2.getProductId()).thenReturn(2L);
        when(product2.getBrandId()).thenReturn(1L);
        when(product2.getLikeCount()).thenReturn(3L);
        ProductStockModel stock1 = mock(ProductStockModel.class);
        ProductStockModel stock2 = mock(ProductStockModel.class);
        when(stock1.getAvailableQty()).thenReturn(50);
        when(stock2.getAvailableQty()).thenReturn(30);
        when(stock1.getProductId()).thenReturn(1L);
        when(stock2.getProductId()).thenReturn(2L);

        BrandModel brand = mock(BrandModel.class);
        when(brand.getBrandId()).thenReturn(1L);
        when(brand.getBrandName()).thenReturn("테스트브랜드");

        PagedResult<ProductModel> pagedResult = new PagedResult<>(List.of(product1, product2), 0, 20, 2, 1);
        ArgumentCaptor<PageQuery> queryCaptor = ArgumentCaptor.forClass(PageQuery.class);
        // keyword가 있으므로 캐시 미대상 경로
        when(productService.findAllForCustomer(eq("키워드"), eq((Long) null), queryCaptor.capture()))
                .thenReturn(pagedResult);
        when(stockService.findAllByProductIds(List.of(1L, 2L))).thenReturn(List.of(stock1, stock2));
        when(brandService.findAllByIds(List.of(1L))).thenReturn(List.of(brand));

        PagedResult<ProductInfo> result = productFacade.getProductsForCustomer(
                "키워드", null, ProductSortType.LIKES_DESC, 0, 20);

        assertThat(result.content()).hasSize(2);
        PageQuery capturedQuery = queryCaptor.getValue();
        assertThat(capturedQuery.sortField()).isEqualTo("likeCount");
        assertThat(capturedQuery.ascending()).isFalse();
    }

    @Test
    @DisplayName("고객용 상품 상세 조회 시 가용 재고, 브랜드명, 좋아요 수가 포함된 ProductInfo를 반환한다")
    void getProductDetailForCustomer_ShouldReturnProductInfoWithAvailableStock() {
        ProductModel product = mock(ProductModel.class);
        when(product.getProductId()).thenReturn(1L);
        when(product.getBrandId()).thenReturn(1L);
        when(product.getLikeCount()).thenReturn(10L);
        ProductStockModel stock = mock(ProductStockModel.class);
        when(stock.getAvailableQty()).thenReturn(70);

        BrandModel brand = mock(BrandModel.class);
        when(brand.getBrandName()).thenReturn("테스트브랜드");

        when(productService.findById(1L)).thenReturn(product);
        when(stockService.findByProductId(1L)).thenReturn(stock);
        when(brandService.findById(1L)).thenReturn(brand);

        ProductInfo result = productFacade.getProductDetailForCustomer(1L);

        assertThat(result).isNotNull();
        assertThat(result.getAvailableStock()).isEqualTo(70);
        assertThat(result.getBrandName()).isEqualTo("테스트브랜드");
        assertThat(result.getLikeCount()).isEqualTo(10L);
        verify(productService).findById(1L);
        verify(stockService).findByProductId(1L);
        verify(brandService).findById(1L);
    }

    @Test
    @DisplayName("상품 생성 시 브랜드 검증 → 상품 생성 → 재고 생성 오케스트레이션이 수행된다")
    void createProduct_ShouldOrchestrateBrandProductStock() {
        BrandModel brand = mock(BrandModel.class);
        when(brandService.findById(1L)).thenReturn(brand);

        ProductModel product = mock(ProductModel.class);
        when(product.getProductId()).thenReturn(1L);
        when(productService.createProduct("테스트상품", 1L, BigDecimal.valueOf(10000), "설명"))
                .thenReturn(product);

        ProductStockModel stock = mock(ProductStockModel.class);
        when(stock.getAvailableQty()).thenReturn(100);
        when(stockService.createStock(1L, 100)).thenReturn(stock);

        ProductCreateCommand command = new ProductCreateCommand("테스트상품", 1L,
                BigDecimal.valueOf(10000), "설명", 100);
        ProductInfo result = productFacade.createProduct(command);

        assertThat(result).isNotNull();
        verify(brandService).findById(1L);
        verify(productService).createProduct("테스트상품", 1L, BigDecimal.valueOf(10000), "설명");
        verify(stockService).createStock(1L, 100);
    }

    @Test
    @DisplayName("상품 수정 후 재고 정보를 결합하여 ProductInfo를 반환한다")
    void updateProduct_ShouldCombineProductAndStock() {
        ProductModel product = mock(ProductModel.class);
        when(product.getProductId()).thenReturn(1L);
        when(productService.updateProduct(1L, "수정상품", BigDecimal.valueOf(20000), "수정설명", null))
                .thenReturn(product);

        ProductStockModel stock = mock(ProductStockModel.class);
        when(stock.getAvailableQty()).thenReturn(50);
        when(stockService.findByProductId(1L)).thenReturn(stock);

        ProductUpdateCommand command = new ProductUpdateCommand(1L, "수정상품",
                BigDecimal.valueOf(20000), "수정설명", null);
        ProductInfo result = productFacade.updateProduct(command);

        assertThat(result).isNotNull();
        verify(productService).updateProduct(1L, "수정상품", BigDecimal.valueOf(20000), "수정설명", null);
        verify(stockService).findByProductId(1L);
    }

    @Test
    @DisplayName("캐시 대상 조건(keyword=null, page=0, size=20) 시 getCachedProductListIds를 통해 ID 캐시 경로로 조회한다")
    void getProductsForCustomer_CacheTarget_ShouldUseCachedIdPath() {
        ProductModel product1 = mock(ProductModel.class);
        when(product1.getProductId()).thenReturn(1L);
        when(product1.getBrandId()).thenReturn(1L);
        when(product1.getLikeCount()).thenReturn(5L);

        ProductStockModel stock1 = mock(ProductStockModel.class);
        when(stock1.getAvailableQty()).thenReturn(50);
        when(stock1.getProductId()).thenReturn(1L);

        BrandModel brand = mock(BrandModel.class);
        when(brand.getBrandId()).thenReturn(1L);
        when(brand.getBrandName()).thenReturn("브랜드");

        // getCachedProductListIds → findAllForCustomer 호출
        PagedResult<ProductModel> pagedResult = new PagedResult<>(List.of(product1), 0, 20, 1, 1);
        when(productService.findAllForCustomer(eq((String) null), eq((Long) null), any(PageQuery.class)))
                .thenReturn(pagedResult);
        // getProductsFromCachedIds에서 개별 findById 호출 (productDetail 캐시 활용)
        when(productService.findById(1L)).thenReturn(product1);
        when(stockService.findAllByProductIds(List.of(1L))).thenReturn(List.of(stock1));
        when(brandService.findAllByIds(List.of(1L))).thenReturn(List.of(brand));

        PagedResult<ProductInfo> result = productFacade.getProductsForCustomer(
                null, null, ProductSortType.LATEST, 0, 20);

        assertThat(result.content()).hasSize(1);
        // 개별 productDetail 캐시 경로를 통해 findById가 호출됨
        verify(productService).findById(1L);
    }
}
