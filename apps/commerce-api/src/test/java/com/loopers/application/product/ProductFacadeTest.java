package com.loopers.application.product;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.like.LikeService;
import com.loopers.application.product.ProductInfo;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.product.ProductStockModel;
import com.loopers.domain.product.StockService;
import com.loopers.interfaces.api.PageResponse;
import com.loopers.support.enums.ProductSortType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

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
    LikeService likeService;

    @InjectMocks
    ProductFacade productFacade;

    @Test
    @DisplayName("고객용 상품 목록 LATEST 정렬 시 페이징된 결과에 브랜드명, 좋아요 수가 포함된다")
    void getProductsForCustomer_WithLatestSort_ShouldReturnPagedResult() {
        ProductModel product1 = mock(ProductModel.class);
        ProductModel product2 = mock(ProductModel.class);
        when(product1.getProductId()).thenReturn("p1");
        when(product1.getBrandId()).thenReturn("b1");
        when(product2.getProductId()).thenReturn("p2");
        when(product2.getBrandId()).thenReturn("b1");
        ProductStockModel stock1 = mock(ProductStockModel.class);
        ProductStockModel stock2 = mock(ProductStockModel.class);
        when(stock1.getAvailableQty()).thenReturn(50);
        when(stock2.getAvailableQty()).thenReturn(30);

        BrandModel brand = mock(BrandModel.class);
        when(brand.getBrandId()).thenReturn("b1");
        when(brand.getBrandName()).thenReturn("테스트브랜드");

        Page<ProductModel> page = new PageImpl<>(List.of(product1, product2));
        when(productService.findAllForCustomer(eq((String) null), eq((String) null), any(Pageable.class)))
                .thenReturn(page);
        when(stockService.findByProductId("p1")).thenReturn(stock1);
        when(stockService.findByProductId("p2")).thenReturn(stock2);
        when(brandService.findAllByIds(List.of("b1"))).thenReturn(List.of(brand));
        when(likeService.countByProductIds(List.of("p1", "p2"))).thenReturn(Map.of("p1", 5L, "p2", 3L));

        PageResponse<ProductInfo> result = productFacade.getProductsForCustomer(
                null, null, ProductSortType.LATEST, 0, 20);

        assertThat(result.content()).hasSize(2);
        assertThat(result.content().get(0).getBrandName()).isEqualTo("테스트브랜드");
        assertThat(result.content().get(0).getLikeCount()).isEqualTo(5L);
        assertThat(result.content().get(1).getLikeCount()).isEqualTo(3L);
    }

    @Test
    @DisplayName("고객용 상품 목록 LIKES_DESC 정렬 시 좋아요 수 내림차순으로 정렬된다")
    void getProductsForCustomer_WithLikesDescSort_ShouldSortByLikeCount() {
        ProductModel product1 = mock(ProductModel.class);
        ProductModel product2 = mock(ProductModel.class);
        when(product1.getProductId()).thenReturn("p1");
        when(product1.getBrandId()).thenReturn("b1");
        when(product2.getProductId()).thenReturn("p2");
        when(product2.getBrandId()).thenReturn("b1");
        ProductStockModel stock1 = mock(ProductStockModel.class);
        ProductStockModel stock2 = mock(ProductStockModel.class);
        when(stock1.getAvailableQty()).thenReturn(50);
        when(stock2.getAvailableQty()).thenReturn(30);

        BrandModel brand = mock(BrandModel.class);
        when(brand.getBrandId()).thenReturn("b1");
        when(brand.getBrandName()).thenReturn("테스트브랜드");

        when(productService.findAllForCustomer(null, null)).thenReturn(List.of(product1, product2));
        when(stockService.findByProductId("p1")).thenReturn(stock1);
        when(stockService.findByProductId("p2")).thenReturn(stock2);
        when(brandService.findAllByIds(List.of("b1"))).thenReturn(List.of(brand));
        when(likeService.countByProductIds(List.of("p1", "p2"))).thenReturn(Map.of("p1", 3L, "p2", 10L));

        PageResponse<ProductInfo> result = productFacade.getProductsForCustomer(
                null, null, ProductSortType.LIKES_DESC, 0, 20);

        assertThat(result.content()).hasSize(2);
        assertThat(result.content().get(0).getLikeCount()).isEqualTo(10L);
        assertThat(result.content().get(1).getLikeCount()).isEqualTo(3L);
    }

    @Test
    @DisplayName("고객용 상품 상세 조회 시 가용 재고, 브랜드명, 좋아요 수가 포함된 ProductInfo를 반환한다")
    void getProductDetailForCustomer_ShouldReturnProductInfoWithAvailableStock() {
        ProductModel product = mock(ProductModel.class);
        when(product.getProductId()).thenReturn("p1");
        when(product.getBrandId()).thenReturn("b1");
        ProductStockModel stock = mock(ProductStockModel.class);
        when(stock.getAvailableQty()).thenReturn(70);

        BrandModel brand = mock(BrandModel.class);
        when(brand.getBrandName()).thenReturn("테스트브랜드");

        when(productService.findById("p1")).thenReturn(product);
        when(stockService.findByProductId("p1")).thenReturn(stock);
        when(brandService.findById("b1")).thenReturn(brand);
        when(likeService.countByProductId("p1")).thenReturn(10L);

        ProductInfo result = productFacade.getProductDetailForCustomer("p1");

        assertThat(result).isNotNull();
        assertThat(result.getAvailableStock()).isEqualTo(70);
        assertThat(result.getBrandName()).isEqualTo("테스트브랜드");
        assertThat(result.getLikeCount()).isEqualTo(10L);
        verify(productService).findById("p1");
        verify(stockService).findByProductId("p1");
        verify(brandService).findById("b1");
        verify(likeService).countByProductId("p1");
    }

    @Test
    @DisplayName("상품 생성 시 브랜드 검증 → 상품 생성 → 재고 생성 오케스트레이션이 수행된다")
    void createProduct_ShouldOrchestrateBrandProductStock() {
        BrandModel brand = mock(BrandModel.class);
        when(brandService.findById("b1")).thenReturn(brand);

        ProductModel product = mock(ProductModel.class);
        when(product.getProductId()).thenReturn("p1");
        when(productService.createProduct("테스트상품", "b1", BigDecimal.valueOf(10000), "설명"))
                .thenReturn(product);

        ProductStockModel stock = mock(ProductStockModel.class);
        when(stock.getAvailableQty()).thenReturn(100);
        when(stockService.createStock("p1", 100)).thenReturn(stock);

        ProductCreateCommand command = new ProductCreateCommand("테스트상품", "b1",
                BigDecimal.valueOf(10000), "설명", 100);
        ProductInfo result = productFacade.createProduct(command);

        assertThat(result).isNotNull();
        verify(brandService).findById("b1");
        verify(productService).createProduct("테스트상품", "b1", BigDecimal.valueOf(10000), "설명");
        verify(stockService).createStock("p1", 100);
    }

    @Test
    @DisplayName("상품 수정 후 재고 정보를 결합하여 ProductInfo를 반환한다")
    void updateProduct_ShouldCombineProductAndStock() {
        ProductModel product = mock(ProductModel.class);
        when(product.getProductId()).thenReturn("p1");
        when(productService.updateProduct("p1", "수정상품", BigDecimal.valueOf(20000), "수정설명", null))
                .thenReturn(product);

        ProductStockModel stock = mock(ProductStockModel.class);
        when(stock.getAvailableQty()).thenReturn(50);
        when(stockService.findByProductId("p1")).thenReturn(stock);

        ProductUpdateCommand command = new ProductUpdateCommand("p1", "수정상품",
                BigDecimal.valueOf(20000), "수정설명", null);
        ProductInfo result = productFacade.updateProduct(command);

        assertThat(result).isNotNull();
        verify(productService).updateProduct("p1", "수정상품", BigDecimal.valueOf(20000), "수정설명", null);
        verify(stockService).findByProductId("p1");
    }

}
