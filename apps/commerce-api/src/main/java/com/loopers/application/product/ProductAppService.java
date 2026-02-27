package com.loopers.application.product;

import com.loopers.domain.product.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 상품 도메인 Application Service.
 *
 * <p>단일 도메인 서비스(ProductService)만 호출하는 얇은 메서드를 담당한다.
 * Model → Info 변환을 수행한다.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductAppService {

    private final ProductService productService;

    /**
     * 상품을 소프트 삭제한다.
     *
     * @param productId 삭제할 상품 ID
     */
    @Transactional
    public void deleteProduct(String productId) {
        productService.deleteProduct(productId);
    }

    /**
     * 상품의 변경 이력(Revision) 목록을 조회한다.
     *
     * @param productId 이력을 조회할 상품 ID
     * @return 상품 변경 이력 Info 목록
     */
    public List<ProductRevisionInfo> getRevisions(String productId) {
        return productService.findRevisionsByProductId(productId).stream()
                .map(ProductRevisionInfo::from)
                .toList();
    }

    /**
     * 특정 상품의 개별 변경 이력 상세를 조회한다.
     *
     * @param productId   상품 ID
     * @param revisionSeq 변경 순번
     * @return 변경 이력 Info
     */
    public ProductRevisionInfo getRevisionDetail(String productId, Long revisionSeq) {
        return ProductRevisionInfo.from(productService.findRevisionById(productId, revisionSeq));
    }
}
