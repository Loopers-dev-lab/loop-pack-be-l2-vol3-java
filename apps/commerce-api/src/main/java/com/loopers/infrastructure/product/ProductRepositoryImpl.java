package com.loopers.infrastructure.product;

import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.support.page.PageQuery;
import com.loopers.support.page.PagedResult;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 도메인 {@link ProductRepository} 인터페이스의 인프라스트럭처 구현체.
 *
 * <p>DIP(의존성 역전 원칙)에 따라 도메인 계층에서 정의한 Repository 인터페이스를 구현하며,
 * 내부적으로 {@link ProductJpaRepository}에 위임하여 실제 데이터 접근을 수행한다.</p>
 */
@Repository
@RequiredArgsConstructor
public class ProductRepositoryImpl implements ProductRepository {

    private final ProductJpaRepository jpaRepository;

    /**
     * 상품을 저장한다.
     *
     * @param product 저장할 상품 엔티티
     * @return 저장된 상품 엔티티 (ID가 자동 생성됨)
     */
    @Override
    public ProductModel save(ProductModel product) {
        return jpaRepository.save(product);
    }

    /**
     * 상품 ID로 상품을 조회한다.
     *
     * @param productId 상품 ID
     * @return 상품 (Optional)
     */
    @Override
    public Optional<ProductModel> findById(Long productId) {
        return jpaRepository.findById(productId);
    }

    @Override
    public Optional<ProductModel> findByIdWithLock(Long productId) {
        return jpaRepository.findByIdWithLock(productId);
    }

    /**
     * 전체 상품 목록을 조회한다.
     *
     * @return 전체 상품 목록
     */
    @Override
    public List<ProductModel> findAll() {
        return jpaRepository.findAll();
    }

    /**
     * 삭제 여부로 상품 목록을 조회한다.
     *
     * @param delYn 삭제 여부 ("N": 미삭제, "Y": 삭제)
     * @return 조건에 부합하는 상품 목록
     */
    @Override
    public List<ProductModel> findAllByDelYn(String delYn) {
        return jpaRepository.findAllByDelYn(delYn);
    }

    /**
     * 고객용 상품 목록을 조회한다.
     *
     * <p>미삭제, 노출 ACTIVE, 판매 ON_SALE 상품을 키워드와 브랜드 ID로 필터링한다.</p>
     *
     * @param keyword 검색 키워드 (null이면 전체)
     * @param brandId 브랜드 ID (null이면 전체)
     * @return 조건에 부합하는 상품 목록
     */
    @Override
    public List<ProductModel> findAllForCustomer(String keyword, Long brandId) {
        return jpaRepository.findAllForCustomer(keyword, brandId);
    }

    /**
     * 브랜드 ID로 상품 목록을 조회한다.
     *
     * @param brandId 브랜드 ID
     * @return 해당 브랜드의 상품 목록
     */
    @Override
    public List<ProductModel> findAllByBrandId(Long brandId) {
        return jpaRepository.findAllByBrandId(brandId);
    }

    /**
     * 상품 ID 목록으로 상품을 일괄 조회한다.
     *
     * @param productIds 상품 ID 목록
     * @return 해당 상품 목록
     */
    @Override
    public List<ProductModel> findAllByProductIds(Collection<Long> productIds) {
        return jpaRepository.findAllById(productIds);
    }

    @Override
    public PagedResult<ProductModel> findAllForCustomer(String keyword, Long brandId, PageQuery query) {
        Sort sort = query.ascending()
                ? Sort.by(Sort.Direction.ASC, query.sortField())
                : Sort.by(Sort.Direction.DESC, query.sortField());
        Page<ProductModel> page = jpaRepository.findAllForCustomerPaged(
                keyword, brandId, PageRequest.of(query.page(), query.size(), sort));
        return new PagedResult<>(page.getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }

    @Override
    public void incrementLikeCount(Long productId) {
        jpaRepository.incrementLikeCount(productId);
    }

    @Override
    public void decrementLikeCount(Long productId) {
        jpaRepository.decrementLikeCount(productId);
    }

}
