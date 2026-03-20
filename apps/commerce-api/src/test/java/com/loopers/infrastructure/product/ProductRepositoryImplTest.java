package com.loopers.infrastructure.product;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.product.ProductModel;
import com.loopers.infrastructure.brand.BrandJpaRepository;
import com.loopers.infrastructure.brand.BrandRepositoryImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import com.loopers.testcontainers.MySqlTestContainersConfig;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import({ProductRepositoryImpl.class, BrandRepositoryImpl.class, MySqlTestContainersConfig.class})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@DisplayName("ProductRepository 통합 테스트")
class ProductRepositoryImplTest {

    @Autowired
    ProductRepositoryImpl productRepository;

    @Autowired
    BrandJpaRepository brandJpaRepository;

    private BrandModel createBrand() {
        return brandJpaRepository.save(BrandModel.create("테스트브랜드", "설명", "서울"));
    }

    private ProductModel createProduct(Long brandId, String name) {
        return ProductModel.create(name, brandId, BigDecimal.valueOf(10000),
                "설명", "카테고리", "블랙", "M", null, null, null);
    }

    @Test
    @DisplayName("저장 시 ID가 자동 생성된다")
    void save_ShouldPersistWithAutoId() {
        BrandModel brand = createBrand();
        ProductModel product = createProduct(brand.getBrandId(), "테스트상품");

        ProductModel saved = productRepository.save(product);

        assertThat(saved.getProductId()).isNotNull();
        assertThat(saved.getProductId()).isGreaterThan(0L);
    }

    @Test
    @DisplayName("ID로 조회 - 존재하는 상품")
    void findById_Existing_ShouldReturn() {
        BrandModel brand = createBrand();
        ProductModel saved = productRepository.save(createProduct(brand.getBrandId(), "테스트상품"));

        Optional<ProductModel> found = productRepository.findById(saved.getProductId());

        assertThat(found).isPresent();
        assertThat(found.get().getProductName()).isEqualTo("테스트상품");
    }

    @Test
    @DisplayName("ID로 조회 - 존재하지 않는 상품")
    void findById_NotExisting_ShouldReturnEmpty() {
        Optional<ProductModel> found = productRepository.findById(999L);

        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("고객 조회 시 ACTIVE + ON_SALE + 미삭제 상품만 반환된다")
    void findAllForCustomer_ShouldReturnOnlyActiveAndNotDeleted() {
        BrandModel brand = createBrand();

        ProductModel active = createProduct(brand.getBrandId(), "활성상품");
        productRepository.save(active);

        ProductModel hidden = createProduct(brand.getBrandId(), "숨김상품");
        hidden.changeDisplayStatus(com.loopers.support.enums.DisplayStatus.HIDDEN);
        productRepository.save(hidden);

        ProductModel stopped = createProduct(brand.getBrandId(), "판매중지상품");
        stopped.changeSaleStatus(com.loopers.support.enums.ProductSaleStatus.STOPPED);
        productRepository.save(stopped);

        ProductModel deleted = createProduct(brand.getBrandId(), "삭제상품");
        deleted.softDelete();
        productRepository.save(deleted);

        List<ProductModel> result = productRepository.findAllForCustomer(null, null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getProductName()).isEqualTo("활성상품");
    }

    @Test
    @DisplayName("고객 조회 시 키워드로 필터링된다")
    void findAllForCustomer_WithKeyword_ShouldFilter() {
        BrandModel brand = createBrand();
        productRepository.save(createProduct(brand.getBrandId(), "봄신상품"));
        productRepository.save(createProduct(brand.getBrandId(), "여름신상품"));
        productRepository.save(createProduct(brand.getBrandId(), "기본티셔츠"));

        List<ProductModel> result = productRepository.findAllForCustomer("신상품", null);

        assertThat(result).hasSize(2);
    }

    @Test
    @DisplayName("brandId로 상품 목록을 조회한다")
    void findAllByBrandId_ShouldReturnMatchingProducts() {
        BrandModel brand1 = createBrand();
        BrandModel brand2 = brandJpaRepository.save(BrandModel.create("다른브랜드", "설명", "부산"));

        productRepository.save(createProduct(brand1.getBrandId(), "상품A"));
        productRepository.save(createProduct(brand1.getBrandId(), "상품B"));
        productRepository.save(createProduct(brand2.getBrandId(), "상품C"));

        List<ProductModel> result = productRepository.findAllByBrandId(brand1.getBrandId());

        assertThat(result).hasSize(2);
    }
}
