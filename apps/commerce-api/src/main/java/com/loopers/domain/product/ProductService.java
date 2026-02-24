package com.loopers.domain.product;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

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
}
