package com.loopers.domain.product;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ProductErrorType;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 상품 도메인 서비스
 *
 * 상품 CRUD 및 상태 관리 비즈니스 로직을 담당한다.
 * 상품 등록 시 브랜드 ACTIVE 검증은 ProductAdminFacade에서 처리한다.
 */
@Component
public class ProductService {

    private final ProductRepository productRepository;

    public ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    /** 상품 생성 (초기 상태: ACTIVE) */
    @Transactional
    public Product create(Long brandId, String name, String description, int basePrice) {
        Product product = Product.create(brandId, name, description, basePrice);
        return productRepository.save(product);
    }

    /**
     * 상품 단건 조회 (Admin용)
     * 삭제된 상품은 ALREADY_DELETED 예외를 던진다.
     */
    @Transactional(readOnly = true)
    public Product getById(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new CoreException(ProductErrorType.PRODUCT_NOT_FOUND));
        product.assertNotDeleted();
        return product;
    }

    /**
     * 노출 가능한 상품 단건 조회 (고객용)
     * 삭제/비노출 상품은 고객에게 "존재하지 않음"으로 처리한다 (404).
     */
    @Transactional(readOnly = true)
    public Product getDisplayableProduct(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new CoreException(ProductErrorType.PRODUCT_NOT_FOUND));
        if (product.getDeletedAt() != null || !product.isDisplayable()) {
            throw new CoreException(ProductErrorType.PRODUCT_NOT_FOUND);
        }
        return product;
    }

    /** 상품 정보 부분 수정 */
    @Transactional
    public Product update(Long id, String name, String description, Integer basePrice) {
        Product product = getById(id);
        product.update(name, description, basePrice);
        return productRepository.save(product);
    }

    /** 상품 상태 변경 (ACTIVE, SOLDOUT, HIDDEN, DISCONTINUED) */
    @Transactional
    public Product changeStatus(Long id, ProductStatus status) {
        Product product = getById(id);
        product.changeStatus(status);
        return productRepository.save(product);
    }

    /** 상품 소프트 삭제 (이미 삭제된 경우 409 Conflict) */
    @Transactional
    public void delete(Long id) {
        Product product = getById(id);
        product.delete();
        productRepository.save(product);
    }

    /** 전체 상품 페이지 조회 (Admin용, brandId 선택 필터) */
    @Transactional(readOnly = true)
    public List<Product> getAllProducts(int page, int size, Long brandId) {
        return productRepository.findAll(page, size, brandId);
    }

    /** 전체 상품 수 조회 (Admin 페이지네이션 메타 정보용) */
    @Transactional(readOnly = true)
    public long countAllProducts(Long brandId) {
        return productRepository.count(brandId);
    }

    /** 노출 가능 상품 페이지 조회 (고객용, brandId 선택 필터) */
    @Transactional(readOnly = true)
    public List<Product> getDisplayableProducts(Long brandId, ProductSortType sort, int page, int size) {
        return productRepository.findAllDisplayable(brandId, sort, page, size);
    }

    /** 노출 가능 상품 수 조회 (고객 페이지네이션 메타 정보용) */
    @Transactional(readOnly = true)
    public long countDisplayableProducts(Long brandId) {
        return productRepository.countDisplayable(brandId);
    }

    /** 브랜드별 ACTIVE 상품 조회 (BrandFacade 고객 상세용) */
    @Transactional(readOnly = true)
    public List<Product> getActiveProductsByBrandId(Long brandId) {
        return productRepository.findAllActiveByBrandId(brandId);
    }

    /** 브랜드별 전체 상품 조회 (BrandAdminFacade 어드민 상세용) */
    @Transactional(readOnly = true)
    public List<Product> getAllProductsByBrandId(Long brandId) {
        return productRepository.findAllByBrandId(brandId);
    }

    /** ID 목록으로 상품 조회 (좋아요 목록용) */
    @Transactional(readOnly = true)
    public List<Product> getProductsByIds(List<Long> ids) {
        return productRepository.findAllByIdIn(ids);
    }

    @Transactional
    public void incrementLikeCount(Long id) {
        productRepository.incrementLikeCount(id);
    }

    @Transactional
    public void decrementLikeCount(Long id) {
        productRepository.decrementLikeCount(id);
    }
}
