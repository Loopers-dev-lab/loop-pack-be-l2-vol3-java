package com.loopers.application.brand;

import com.loopers.domain.brand.*;
import com.loopers.domain.product.Product;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.domain.product.ProductRepository;
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
import static org.mockito.Mockito.*;

class BrandApplicationServiceTest {

    private BrandRepository brandRepository;
    private ProductRepository productRepository;
    private BrandApplicationService brandApplicationService;

    @BeforeEach
    void setUp() {
        brandRepository = mock(BrandRepository.class);
        productRepository = mock(ProductRepository.class);
        brandApplicationService = new BrandApplicationService(
            brandRepository,
            productRepository,
            new BrandDomainService()
        );
    }

    @DisplayName("register: 유효한 브랜드 정보로 생성에 성공한다")
    @Test
    void register_success() {
        when(brandRepository.existsByName("LOOPERS")).thenReturn(false);
        when(brandRepository.save(any(Brand.class))).thenAnswer(invocation -> invocation.getArgument(0));

        BrandDto.BrandInfo brandInfo = brandApplicationService.register("LOOPERS", "브랜드 설명");

        assertThat(brandInfo.name()).isEqualTo("LOOPERS");
        assertThat(brandInfo.description()).isEqualTo("브랜드 설명");
        verify(brandRepository).save(any(Brand.class));
    }

    @DisplayName("register: 활성 브랜드 이름이 중복되면 예외가 발생한다")
    @Test
    void register_fail_duplicateName() {
        when(brandRepository.existsByName("LOOPERS")).thenReturn(true);

        assertThatThrownBy(() -> brandApplicationService.register("LOOPERS", "브랜드 설명"))
            .isInstanceOf(CoreException.class)
            .satisfies(e -> assertThat(((CoreException) e).getErrorType())
                .isEqualTo(ErrorType.DUPLICATE_BRAND_NAME));

        verify(brandRepository, never()).save(any(Brand.class));
    }

    @DisplayName("getAdminBrands: 활성 브랜드 목록을 페이지로 조회한다")
    @Test
    void getAdminBrands_success() {
        PageRequest pageRequest = PageRequest.of(0, 20);
        Brand brand = Brand.create(new BrandName("LOOPERS"), new BrandDescription("desc"));
        when(brandRepository.findAll(pageRequest)).thenReturn(new PageImpl<>(List.of(brand)));

        Page<BrandDto.BrandInfo> result = brandApplicationService.getAdminBrands(pageRequest);

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().getFirst().name()).isEqualTo("LOOPERS");
    }

    @DisplayName("getBrand: 존재하는 브랜드를 단건 조회한다")
    @Test
    void getBrand_success() {
        Long brandId = 1L;
        Brand brand = Brand.create(new BrandName("LOOPERS"), new BrandDescription("desc"));
        when(brandRepository.findById(brandId)).thenReturn(Optional.of(brand));

        BrandDto.BrandInfo result = brandApplicationService.getBrand(brandId);

        assertThat(result.name()).isEqualTo("LOOPERS");
    }

    @DisplayName("getBrand: 존재하지 않는 브랜드면 예외가 발생한다")
    @Test
    void getBrand_fail_notFound() {
        when(brandRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> brandApplicationService.getBrand(99L))
            .isInstanceOf(CoreException.class)
            .satisfies(e -> assertThat(((CoreException) e).getErrorType())
                .isEqualTo(ErrorType.BRAND_NOT_FOUND));
    }

    @DisplayName("update: 브랜드 정보를 수정한다")
    @Test
    void update_success() {
        Long brandId = 1L;
        Brand brand = Brand.create(new BrandName("OLD"), new BrandDescription("OLD_DESC"));

        when(brandRepository.findById(brandId)).thenReturn(Optional.of(brand));
        when(brandRepository.existsByName("NEW")).thenReturn(false);
        when(brandRepository.save(brand)).thenReturn(brand);

        BrandDto.BrandInfo result = brandApplicationService.update(brandId, "NEW", "NEW_DESC");

        assertThat(result.name()).isEqualTo("NEW");
        assertThat(result.description()).isEqualTo("NEW_DESC");
    }

    @DisplayName("update: 같은 이름으로 수정하면 중복 체크를 건너뛰고 성공한다")
    @Test
    void update_success_sameName() {
        Long brandId = 1L;
        Brand brand = Brand.create(new BrandName("LOOPERS"), new BrandDescription("OLD_DESC"));

        when(brandRepository.findById(brandId)).thenReturn(Optional.of(brand));
        when(brandRepository.save(brand)).thenReturn(brand);

        BrandDto.BrandInfo result = brandApplicationService.update(brandId, "LOOPERS", "NEW_DESC");

        assertThat(result.name()).isEqualTo("LOOPERS");
        assertThat(result.description()).isEqualTo("NEW_DESC");
        verify(brandRepository, never()).existsByName(any());
    }

    @DisplayName("update: 존재하지 않는 브랜드면 예외가 발생한다")
    @Test
    void update_fail_notFound() {
        when(brandRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> brandApplicationService.update(1L, "NEW", "DESC"))
            .isInstanceOf(CoreException.class)
            .satisfies(e -> assertThat(((CoreException) e).getErrorType())
                .isEqualTo(ErrorType.BRAND_NOT_FOUND));
    }

    @DisplayName("delete: 브랜드 soft delete 시 브랜드 소속 활성 상품도 함께 soft delete한다")
    @Test
    void delete_success_withProductCascade() {
        Long brandId = 1L;
        Brand brand = Brand.create(new BrandName("LOOPERS"), new BrandDescription("DESC"));
        Product product1 = createProduct(brandId, "A");
        Product product2 = createProduct(brandId, "B");

        when(brandRepository.findById(brandId)).thenReturn(Optional.of(brand));
        when(brandRepository.save(brand)).thenReturn(brand);
        when(productRepository.findAllByBrandId(brandId)).thenReturn(List.of(product1, product2));

        brandApplicationService.delete(brandId);

        assertThat(brand.isDeleted()).isTrue();
        assertThat(product1.isDeleted()).isTrue();
        assertThat(product2.isDeleted()).isTrue();
        verify(productRepository, times(2)).save(any(Product.class));
    }

    @DisplayName("restore: 삭제된 브랜드 복구 시 삭제된 상품도 함께 복구한다")
    @Test
    void restore_success_withProductCascade() {
        Long brandId = 1L;
        Brand deletedBrand = Brand.create(new BrandName("LOOPERS"), new BrandDescription("DESC"));
        deletedBrand.delete();

        Product deletedProduct = createProduct(brandId, "A");
        deletedProduct.delete();
        Product activeProduct = createProduct(brandId, "B");

        when(brandRepository.findByIdIncludingDeleted(brandId)).thenReturn(Optional.of(deletedBrand));
        when(brandRepository.save(deletedBrand)).thenReturn(deletedBrand);
        when(productRepository.findAllByBrandIdIncludingDeleted(brandId))
            .thenReturn(List.of(deletedProduct, activeProduct));

        BrandDto.BrandInfo restored = brandApplicationService.restore(brandId);

        assertThat(restored.isDeleted()).isFalse();
        assertThat(deletedBrand.isDeleted()).isFalse();
        assertThat(deletedProduct.isDeleted()).isFalse();
        assertThat(activeProduct.isDeleted()).isFalse();
        verify(productRepository, times(1)).save(deletedProduct);
        verify(productRepository, never()).save(activeProduct);
    }

    @DisplayName("restore: 삭제되지 않은 브랜드 복구 요청 시 예외가 발생한다")
    @Test
    void restore_fail_notDeleted() {
        Long brandId = 1L;
        Brand activeBrand = Brand.create(new BrandName("LOOPERS"), new BrandDescription("DESC"));
        when(brandRepository.findByIdIncludingDeleted(brandId)).thenReturn(Optional.of(activeBrand));

        assertThatThrownBy(() -> brandApplicationService.restore(brandId))
            .isInstanceOf(CoreException.class)
            .satisfies(e -> assertThat(((CoreException) e).getErrorType())
                .isEqualTo(ErrorType.BRAND_NOT_DELETED));
    }

    private Product createProduct(Long brandId, String name) {
        return Product.create(
            brandId,
            name,
            "product-desc",
            BigDecimal.valueOf(1000),
            10
        );
    }
}
