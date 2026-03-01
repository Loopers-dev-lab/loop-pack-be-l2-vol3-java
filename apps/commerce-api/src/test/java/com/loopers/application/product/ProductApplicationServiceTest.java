package com.loopers.application.product;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandDescription;
import com.loopers.domain.brand.BrandName;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductDomainService;
import com.loopers.domain.product.ProductRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.*;

class ProductApplicationServiceTest {

    private ProductRepository productRepository;
    private BrandRepository brandRepository;
    private ProductApplicationService productApplicationService;

    private Brand brand;

    @BeforeEach
    void setUp() {
        productRepository = mock(ProductRepository.class);
        brandRepository = mock(BrandRepository.class);
        productApplicationService = new ProductApplicationService(
            productRepository,
            brandRepository,
            new ProductDomainService()
        );

        brand = Brand.create(new BrandName("LOOPERS"), new BrandDescription("루퍼스 브랜드"));
    }

    // ── register ──────────────────────────────────────────────────────────────

    @DisplayName("register: 브랜드가 존재하면 상품 등록에 성공하고 브랜드 이름이 포함된다")
    @Test
    void register_success() {
        Long brandId = 1L;
        Product product = Product.create(brandId, "신발", "설명", BigDecimal.valueOf(50000), 100);

        when(brandRepository.findById(brandId)).thenReturn(Optional.of(brand));
        when(productRepository.save(any(Product.class))).thenReturn(product);

        ProductDto.ProductInfo result = productApplicationService.register(
            brandId, "신발", "설명", BigDecimal.valueOf(50000), 100
        );

        assertThat(result.name()).isEqualTo("신발");
        assertThat(result.brandName()).isEqualTo("LOOPERS");
        assertThat(result.price()).isEqualByComparingTo(BigDecimal.valueOf(50000));
        assertThat(result.stock()).isEqualTo(100);
        verify(productRepository).save(any(Product.class));
    }

    @DisplayName("register: 존재하지 않는 브랜드면 예외가 발생한다")
    @Test
    void register_fail_brandNotFound() {
        when(brandRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
            productApplicationService.register(99L, "신발", "설명", BigDecimal.valueOf(50000), 100)
        ).isInstanceOf(CoreException.class)
         .satisfies(e -> assertThat(((CoreException) e).getErrorType())
             .isEqualTo(ErrorType.BRAND_NOT_FOUND));

        verify(productRepository, never()).save(any());
    }

    // ── getProducts ───────────────────────────────────────────────────────────

    @DisplayName("getProducts: LATEST 정렬로 상품 목록을 조회하면 브랜드 이름이 포함된다")
    @Test
    void getProducts_latest_success() {
        PageRequest pageRequest = PageRequest.of(0, 20);
        // brand.getId()는 비영속 상태에서 0L(BaseEntity 기본값)이므로
        // product의 brandId도 0L로 맞춰 Map 키 일치 보장
        Product product = Product.create(brand.getId(), "신발", "설명", BigDecimal.valueOf(50000), 10);

        when(productRepository.findAll(any(PageRequest.class))).thenReturn(new PageImpl<>(List.of(product)));
        when(brandRepository.findAllByIds(anyCollection())).thenReturn(List.of(brand));

        Page<ProductDto.ProductInfo> result = productApplicationService.getProducts(ProductSortType.LATEST, pageRequest);

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().getFirst().name()).isEqualTo("신발");
        assertThat(result.getContent().getFirst().brandName()).isEqualTo("LOOPERS");
    }

    @DisplayName("getProducts: PRICE_ASC 정렬로 조회 시 price 오름차순 Sort가 적용된다")
    @Test
    void getProducts_priceAsc_success() {
        PageRequest pageRequest = PageRequest.of(0, 20);
        when(productRepository.findAll(any(PageRequest.class))).thenReturn(new PageImpl<>(List.of()));
        when(brandRepository.findAllByIds(anyCollection())).thenReturn(List.of());

        productApplicationService.getProducts(ProductSortType.PRICE_ASC, pageRequest);

        verify(productRepository).findAll(argThat((PageRequest pr) ->
            pr.getSort().getOrderFor("price") != null &&
            pr.getSort().getOrderFor("price").getDirection().isAscending()
        ));
    }

    @DisplayName("getProducts: LIKES_DESC 정렬로 조회 시 likeCount 내림차순 Sort가 적용된다")
    @Test
    void getProducts_likesDesc_success() {
        PageRequest pageRequest = PageRequest.of(0, 20);
        when(productRepository.findAll(any(PageRequest.class))).thenReturn(new PageImpl<>(List.of()));
        when(brandRepository.findAllByIds(anyCollection())).thenReturn(List.of());

        productApplicationService.getProducts(ProductSortType.LIKES_DESC, pageRequest);

        verify(productRepository).findAll(argThat((PageRequest pr) ->
            pr.getSort().getOrderFor("likeCount") != null &&
            pr.getSort().getOrderFor("likeCount").getDirection().isDescending()
        ));
    }

    // ── getProductsByBrand ────────────────────────────────────────────────────

    @DisplayName("getProductsByBrand: 브랜드별 상품 목록 조회 시 브랜드 이름이 포함된다")
    @Test
    void getProductsByBrand_success() {
        Long brandId = 1L;
        PageRequest pageRequest = PageRequest.of(0, 20);
        Product product1 = Product.create(brandId, "신발A", "설명", BigDecimal.valueOf(50000), 10);
        Product product2 = Product.create(brandId, "신발B", "설명", BigDecimal.valueOf(60000), 5);

        when(brandRepository.findById(brandId)).thenReturn(Optional.of(brand));
        when(productRepository.findByBrandId(eq(brandId), any(PageRequest.class)))
            .thenReturn(new PageImpl<>(List.of(product1, product2)));

        Page<ProductDto.ProductInfo> result = productApplicationService.getProductsByBrand(
            brandId, ProductSortType.LATEST, pageRequest
        );

        assertThat(result.getTotalElements()).isEqualTo(2);
        assertThat(result.getContent()).extracting(ProductDto.ProductInfo::brandName)
            .containsOnly("LOOPERS");
    }

    @DisplayName("getProductsByBrand: 존재하지 않는 브랜드면 예외가 발생한다")
    @Test
    void getProductsByBrand_fail_brandNotFound() {
        when(brandRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
            productApplicationService.getProductsByBrand(99L, ProductSortType.LATEST, PageRequest.of(0, 20))
        ).isInstanceOf(CoreException.class)
         .satisfies(e -> assertThat(((CoreException) e).getErrorType())
             .isEqualTo(ErrorType.BRAND_NOT_FOUND));
    }

    // ── getProduct ────────────────────────────────────────────────────────────

    @DisplayName("getProduct: 상품 단건 조회 시 브랜드 이름이 포함된다")
    @Test
    void getProduct_success() {
        Long productId = 1L;
        Product product = Product.create(1L, "신발", "설명", BigDecimal.valueOf(50000), 10);

        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(brandRepository.findById(product.getBrandId())).thenReturn(Optional.of(brand));

        ProductDto.ProductInfo result = productApplicationService.getProduct(productId);

        assertThat(result.name()).isEqualTo("신발");
        assertThat(result.brandName()).isEqualTo("LOOPERS");
    }

    @DisplayName("getProduct: 존재하지 않는 상품 조회 시 예외가 발생한다")
    @Test
    void getProduct_fail_notFound() {
        when(productRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productApplicationService.getProduct(99L))
            .isInstanceOf(CoreException.class)
            .satisfies(e -> assertThat(((CoreException) e).getErrorType())
                .isEqualTo(ErrorType.PRODUCT_NOT_FOUND));
    }

    // ── increaseStock ─────────────────────────────────────────────────────────

    @DisplayName("increaseStock: 수량만큼 재고를 증가시키고 브랜드 이름이 포함된다")
    @Test
    void increaseStock_success() {
        Long productId = 1L;
        Product product = Product.create(1L, "신발", "설명", BigDecimal.valueOf(50000), 10);

        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(productRepository.save(product)).thenReturn(product);
        when(brandRepository.findById(product.getBrandId())).thenReturn(Optional.of(brand));

        ProductDto.ProductInfo result = productApplicationService.increaseStock(productId, 5);

        assertThat(result.stock()).isEqualTo(15);
        assertThat(result.brandName()).isEqualTo("LOOPERS");
    }

    @DisplayName("increaseStock: 존재하지 않는 상품이면 예외가 발생한다")
    @Test
    void increaseStock_fail_productNotFound() {
        when(productRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productApplicationService.increaseStock(99L, 5))
            .isInstanceOf(CoreException.class)
            .satisfies(e -> assertThat(((CoreException) e).getErrorType())
                .isEqualTo(ErrorType.PRODUCT_NOT_FOUND));
    }

    @DisplayName("increaseStock: 수량이 0 이하이면 도메인에서 예외가 발생한다")
    @Test
    void increaseStock_fail_invalidQuantity() {
        Long productId = 1L;
        Product product = Product.create(1L, "신발", "설명", BigDecimal.valueOf(50000), 10);
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));

        assertThatThrownBy(() -> productApplicationService.increaseStock(productId, 0))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("수량은 1 이상이어야 합니다");
    }

    // ── decreaseStock ─────────────────────────────────────────────────────────

    @DisplayName("decreaseStock: 재고가 충분하면 수량만큼 차감하고 음수가 되지 않는다")
    @Test
    void decreaseStock_success() {
        Long productId = 1L;
        Product product = Product.create(1L, "신발", "설명", BigDecimal.valueOf(50000), 10);

        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(productRepository.save(product)).thenReturn(product);
        when(brandRepository.findById(product.getBrandId())).thenReturn(Optional.of(brand));

        ProductDto.ProductInfo result = productApplicationService.decreaseStock(productId, 3);

        assertThat(result.stock()).isEqualTo(7);
        assertThat(result.stock()).isGreaterThanOrEqualTo(0);
    }

    @DisplayName("decreaseStock: 재고보다 많은 수량 차감 시 도메인에서 예외가 발생한다")
    @Test
    void decreaseStock_fail_notEnoughStock() {
        Long productId = 1L;
        Product product = Product.create(1L, "신발", "설명", BigDecimal.valueOf(50000), 2);
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));

        assertThatThrownBy(() -> productApplicationService.decreaseStock(productId, 5))
            .isInstanceOf(CoreException.class)
            .satisfies(e -> assertThat(((CoreException) e).getErrorType())
                .isEqualTo(ErrorType.PRODUCT_INSUFFICIENT_STOCK));
    }

    @DisplayName("decreaseStock: 존재하지 않는 상품이면 예외가 발생한다")
    @Test
    void decreaseStock_fail_productNotFound() {
        when(productRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productApplicationService.decreaseStock(99L, 3))
            .isInstanceOf(CoreException.class)
            .satisfies(e -> assertThat(((CoreException) e).getErrorType())
                .isEqualTo(ErrorType.PRODUCT_NOT_FOUND));
    }

    // ── delete ────────────────────────────────────────────────────────────────

    @DisplayName("delete: 활성 상품을 soft delete한다")
    @Test
    void delete_success() {
        Long productId = 1L;
        Product product = Product.create(1L, "신발", "설명", BigDecimal.valueOf(50000), 10);
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));

        productApplicationService.delete(productId);

        assertThat(product.isDeleted()).isTrue();
        verify(productRepository).save(product);
    }

    @DisplayName("delete: 존재하지 않는 상품이면 예외가 발생한다")
    @Test
    void delete_fail_notFound() {
        when(productRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productApplicationService.delete(99L))
            .isInstanceOf(CoreException.class)
            .satisfies(e -> assertThat(((CoreException) e).getErrorType())
                .isEqualTo(ErrorType.PRODUCT_NOT_FOUND));
    }

    // ── restore ───────────────────────────────────────────────────────────────

    @DisplayName("restore: 삭제된 상품을 복구하고 브랜드 이름이 포함된다")
    @Test
    void restore_success() {
        Long productId = 1L;
        Product product = Product.create(1L, "신발", "설명", BigDecimal.valueOf(50000), 10);
        product.delete();

        when(productRepository.findByIdIncludingDeleted(productId)).thenReturn(Optional.of(product));
        when(productRepository.save(product)).thenReturn(product);
        when(brandRepository.findByIdIncludingDeleted(product.getBrandId())).thenReturn(Optional.of(brand));

        ProductDto.ProductInfo result = productApplicationService.restore(productId);

        assertThat(result.isDeleted()).isFalse();
        assertThat(result.brandName()).isEqualTo("LOOPERS");
    }

    @DisplayName("restore: 존재하지 않는 상품이면 예외가 발생한다")
    @Test
    void restore_fail_notFound() {
        when(productRepository.findByIdIncludingDeleted(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productApplicationService.restore(99L))
            .isInstanceOf(CoreException.class)
            .satisfies(e -> assertThat(((CoreException) e).getErrorType())
                .isEqualTo(ErrorType.PRODUCT_NOT_FOUND));
    }

    @DisplayName("restore: 삭제되지 않은 상품 복구 요청 시 예외가 발생한다")
    @Test
    void restore_fail_notDeleted() {
        Long productId = 1L;
        Product product = Product.create(1L, "신발", "설명", BigDecimal.valueOf(50000), 10);
        when(productRepository.findByIdIncludingDeleted(productId)).thenReturn(Optional.of(product));

        assertThatThrownBy(() -> productApplicationService.restore(productId))
            .isInstanceOf(CoreException.class)
            .satisfies(e -> assertThat(((CoreException) e).getErrorType())
                .isEqualTo(ErrorType.PRODUCT_NOT_DELETED));
    }
}
