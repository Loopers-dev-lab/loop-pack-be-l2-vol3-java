package com.loopers.application.brand;

import com.loopers.domain.PageResult;
import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandDomainService;
import com.loopers.domain.product.ProductDomainService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Set;

@RequiredArgsConstructor
@Component
public class BrandApplicationService {

    private final BrandDomainService brandService;
    private final ProductDomainService productService;

    @Transactional
    public Brand register(String name) {
        return brandService.register(name);
    }

    @Transactional(readOnly = true)
    public Brand getById(Long id) {
        return brandService.getById(id);
    }

    @Transactional(readOnly = true)
    public Map<Long, Brand> getByIds(Set<Long> ids) {
        return brandService.getByIds(ids);
    }

    @Transactional(readOnly = true)
    public PageResult<Brand> getAll(int page, int size) {
        return brandService.getAll(page, size);
    }

    @Transactional
    public Brand update(Long id, String name) {
        return brandService.update(id, name);
    }

    /**
     * 단일 트랜잭션에서 Product aggregate와 Brand aggregate를 함께 수정한다.
     * "하나의 트랜잭션 = 하나의 Aggregate" 원칙의 의도적 예외:
     * 브랜드 삭제 시 소속 상품이 남아있으면 orphan 데이터가 발생하므로
     * 참조 무결성을 위해 원자적으로 처리한다.
     * 추후 Event로 처리
     */
    @Transactional
    public void delete(Long id) {
        brandService.delete(id);
        productService.deleteAllByBrandId(id);
    }
}
