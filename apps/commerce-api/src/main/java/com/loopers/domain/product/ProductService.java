package com.loopers.domain.product;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
@Component
public class ProductService {

    private final ProductRepository productRepository;

    // 상품 등록
    @Transactional
    public Product register(Long brandId, String name, Money price, Stock stock) {
        if (productRepository.existsByBrandIdAndName(brandId, name)) {
            throw new CoreException(ErrorType.CONFLICT, "해당 브랜드에 이미 존재하는 상품명입니다.");
        }
        Product product = new Product(brandId, name, price, stock);
        return productRepository.save(product);
    }

    // 상품 상세 조회
    @Transactional(readOnly = true)
    public Product findById(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 상품입니다."));
    }

    // 상품 목록 조회 (brandId 필터 선택)
    @Transactional(readOnly = true)
    public Page<Product> findAll(Long brandId, Pageable pageable) {
        if (brandId == null) {
            return productRepository.findAll(pageable);
        }
        return productRepository.findAllByBrandId(brandId, pageable);
    }

    // 상품 정보 수정
    @Transactional
    public Product update(Long id, String name, Money price, Stock stock) {
        Product product = findById(id);
        if (productRepository.existsByBrandIdAndNameAndIdNot(product.getBrandId(), name, id)) {
            throw new CoreException(ErrorType.CONFLICT, "해당 브랜드에 이미 존재하는 상품명입니다.");
        }
        product.update(name, price, stock);
        return product;
    }

    // 상품 삭제
    @Transactional
    public void delete(Long id) {
        Product product = findById(id);
        product.delete();
    }

    // 브랜드 삭제 시 해당 브랜드 상품 전체 삭제 (cascade용)
    @Transactional
    public void deleteAllByBrandId(Long brandId) {
        productRepository.deleteAllByBrandId(brandId);
    }

    // 브랜드 삭제 시 좋아요 cascade를 위한 상품 ID 목록 조회
    @Transactional(readOnly = true)
    public List<Long> findIdsByBrandId(Long brandId) {
        return productRepository.findIdsByBrandId(brandId);
    }

    // ID 목록으로 상품 일괄 조회 (좋아요 목록에서 상품 정보 조합 시 사용)
    @Transactional(readOnly = true)
    public List<Product> findAllByIds(List<Long> ids) {
        return productRepository.findAllByIds(ids);
    }

    // 원자적 좋아요 수 증가 (US-L01) - DB 레벨 UPDATE로 동시성 보장
    @Transactional
    public void increaseLikeCount(Long productId) {
        productRepository.incrementLikeCount(productId);
    }

    // 원자적 좋아요 수 감소 (US-L02) - DB 레벨 UPDATE로 동시성 보장
    @Transactional
    public void decreaseLikeCount(Long productId) {
        productRepository.decrementLikeCount(productId);
    }

    /**
     * 비관적 락으로 재고 확인만 수행 (차감 제외).
     * 쿠폰 검증을 거친 후 재고를 차감해야 하므로 확인과 차감을 분리한다.
     * SELECT FOR UPDATE 락은 호출한 @Transactional 컨텍스트에서 유지된다.
     */
    @Transactional
    public List<Product> findAllAndVerifyStock(Map<Long, Quantity> quantityByProductId) {
        // 데드락 방지: 항상 PK 오름차순으로 락을 획득해 circular wait 제거
        List<Long> productIds = quantityByProductId.keySet().stream().sorted().toList();
        // 재고 확인을 위해 비관적 락으로 상품 조회
        List<Product> products = productRepository.findAllByIdsForUpdate(productIds);

        if (products.size() != productIds.size()) {
            throw new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 상품이 포함되어 있습니다.");
        }

        for (Product product : products) {
            Quantity quantity = quantityByProductId.get(product.getId());
            if (!product.getStock().hasEnough(quantity)) {
                throw new CoreException(ErrorType.BAD_REQUEST,
                        "상품의 재고가 부족합니다: " + product.getName()
                        + " (현재 재고: " + product.getStock().getQuantity()
                        + "개, 주문 수량: " + quantity.getValue() + "개)");
            }
        }

        return products;
    }

}
