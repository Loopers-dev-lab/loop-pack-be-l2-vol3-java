package com.loopers.infrastructure.product;

import com.loopers.application.product.ProductQueryService;
import com.loopers.application.product.ProductReadCache;
import com.loopers.application.product.ProductReadModel;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class ProductQueryServiceImpl implements ProductQueryService {

    private final ProductJpaRepository productJpaRepository;
    private final ProductReadCache productReadCache;

    @Override
    public ProductReadModel getById(Long id) {
        ProductReadModel result = productReadCache.get(id, () ->
            productJpaRepository.findByIdAndDeletedAtIsNull(id)
                .map(ProductReadModel::from)
                .orElse(null)
        );
        if (result == null) {
            throw new CoreException(ErrorType.NOT_FOUND, "상품을 찾을 수 없습니다.");
        }
        return result;
    }
}
