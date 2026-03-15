package com.loopers.domain.product;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Slice;
import org.springframework.transaction.annotation.Transactional;

import com.loopers.domain.shared.annotation.DomainService;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.support.page.Page;
import com.loopers.support.page.PageSize;

import lombok.RequiredArgsConstructor;

/**
 * 상품 도메인의 핵심 비즈니스 규칙을 담당하는 도메인 서비스.
 */
@DomainService
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;

    /**
     * 새로운 상품을 생성한다.
     *
     * @param spec 상품 생성 스펙
     * @return 생성된 상품
     */
    @Transactional
    public Product create(ProductSpec spec) {
        Product product = Product.create(spec);
        return productRepository.save(product);
    }

    /**
     * 활성 상태의 상품을 단건 조회한다.
     *
     * @param productId 상품 ID
     * @return 활성 상품
     * @throws CoreException 상품이 존재하지 않거나 삭제된 경우
     */
    public Product getActiveProduct(Long productId) {
        return productRepository.findByIdAndDeletedAtIsNull(productId)
                .orElseThrow(() -> new CoreException(ErrorType.PRODUCT_NOT_FOUND));
    }

    /**
     * 상품을 페이지 단위로 조회한다 (어드민용).
     *
     * @param brandId  브랜드 ID (null이면 전체 조회)
     * @param pageSize 페이지 크기
     * @return 상품 목록 페이지
     */
    public Page<Product> getProducts(Long brandId, PageSize pageSize) {
        Slice<Product> products = productRepository.findAll(
                brandId,
                pageSize.toPageable(ProductSortType.DEFAULT.getSort())
        );
        return new Page<>(products.getContent(), products.hasNext());
    }

    /**
     * 활성 상태의 상품을 정렬/필터 조건에 따라 페이지 단위로 조회한다.
     *
     * @param brandId  브랜드 ID (null이면 전체 조회)
     * @param sortType 정렬 기준
     * @param pageSize 페이지 크기
     * @return 활성 상품 목록 페이지
     */
    public Page<Product> getActiveProducts(Long brandId, ProductSortType sortType, PageSize pageSize) {
        Slice<Product> products = productRepository.findAllActiveProducts(brandId, sortType, pageSize.toPageable());
        return new Page<>(products.getContent(), products.hasNext());
    }

    /**
     * 상품 ID 목록으로 활성 상품 맵을 조회한다.
     *
     * @param productIds 상품 ID 목록
     * @return 상품 ID를 키로 하는 상품 맵
     */
    public Map<Long, Product> getActiveProductsByIds(List<Long> productIds) {
        return productRepository.findAllByIdInAndDeletedAtIsNull(productIds)
                .stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));
    }

    /**
     * 특정 브랜드의 활성 상품 ID 목록을 조회한다.
     *
     * @param brandId 브랜드 ID
     * @return 활성 상품 ID 목록
     */
    public List<Long> getActiveProductIdsByBrandId(Long brandId) {
        return productRepository.findAllByBrandIdAndDeletedAtIsNull(brandId)
                .stream()
                .map(Product::getId)
                .toList();
    }

    /**
     * 상품 정보를 수정한다.
     *
     * @param product 상품 수정 정보 (productId 포함)
     * @return 수정된 상품
     * @throws CoreException 상품이 존재하지 않는 경우
     */
    @Transactional
    public Product update(ModifyProduct product) {
        Product entity = productRepository.findById(product.productId())
                .orElseThrow(() -> new CoreException(ErrorType.PRODUCT_NOT_FOUND));
        entity.update(product);
        return entity;
    }

    /**
     * 상품을 소프트 삭제한다.
     *
     * @param productId 상품 ID
     * @return 실제로 삭제가 수행되었으면 true, 이미 삭제된 상태면 false
     * @throws CoreException 상품이 존재하지 않는 경우
     */
    @Transactional
    public boolean delete(Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new CoreException(ErrorType.PRODUCT_NOT_FOUND));
        if (product.isDeleted()) {
            return false;
        }
        product.delete();
        return true;
    }

    /**
     * 특정 브랜드의 모든 상품을 논리 삭제한다.
     *
     * @param brandId 브랜드 ID
     */
    @Transactional
    public void softDeleteAllByBrandId(Long brandId) {
        productRepository.softDeleteAllByBrandId(brandId);
    }

    /**
     * 상품의 재고를 차감한다. 비관적 락으로 조회 후 차감한다.
     *
     * @param productId 상품 ID
     * @param quantity  차감할 수량
     * @throws CoreException 상품이 존재하지 않거나 삭제된 경우
     * @throws CoreException 품절 상품인 경우 ({@code SOLD_OUT_PRODUCT})
     * @throws CoreException 재고가 부족한 경우 ({@code INSUFFICIENT_STOCK})
     */
    @Transactional
    public void deductStock(Long productId, long quantity) {
        Product product = productRepository.findByIdAndDeletedAtIsNullForUpdate(productId)
                .orElseThrow(() -> new CoreException(ErrorType.PRODUCT_NOT_FOUND));
        product.deductStock(quantity);
    }

    /**
     * 상품의 좋아요 수를 1 증가시킨다. 아토믹 업데이트로 동시성을 보장한다.
     *
     * @param productId 상품 ID
     * @throws CoreException 상품이 존재하지 않거나 삭제된 경우
     */
    @Transactional
    public void increaseLikeCount(Long productId) {
        int updated = productRepository.incrementLikeCount(productId);
        if (updated == 0) {
            throw new CoreException(ErrorType.PRODUCT_NOT_FOUND);
        }
    }

    /**
     * 상품의 좋아요 수를 1 감소시킨다. 아토믹 업데이트로 동시성을 보장한다.
     * likeCount가 이미 0이면 무시한다 (호출부에서 상품 존재를 검증해야 한다).
     *
     * @param productId 상품 ID
     */
    @Transactional
    public void decreaseLikeCount(Long productId) {
        productRepository.decrementLikeCount(productId);
    }

    /**
     * 활성 상태의 상품 존재 여부를 검증한다.
     *
     * @param productId 상품 ID
     * @throws CoreException 상품이 존재하지 않거나 삭제된 경우
     */
    public void validateActiveProductExists(Long productId) {
        if (!productRepository.existsByIdAndDeletedAtIsNull(productId)) {
            throw new CoreException(ErrorType.PRODUCT_NOT_FOUND);
        }
    }
}
