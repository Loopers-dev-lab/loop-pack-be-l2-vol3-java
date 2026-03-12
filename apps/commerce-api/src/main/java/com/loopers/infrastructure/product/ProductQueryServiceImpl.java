package com.loopers.infrastructure.product;

import com.loopers.application.product.ProductPageReadCache;
import com.loopers.application.product.ProductQueryService;
import com.loopers.application.product.ProductReadCache;
import com.loopers.application.product.ProductReadModel;
import com.loopers.domain.PageResult;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductSortType;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class ProductQueryServiceImpl implements ProductQueryService {

    private final ProductJpaRepository productJpaRepository;
    private final ProductReadCache productReadCache;
    private final ProductPageReadCache productPageReadCache;

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

    @Override
    public PageResult<ProductReadModel> getAll(Long brandId, ProductSortType sort, int page, int size) {
        if (brandId != null) {
            return queryFromDb(brandId, sort, page, size);
        }
        return productPageReadCache.get(sort, page, size, () -> queryFromDb(null, sort, page, size));
    }

    private PageResult<ProductReadModel> queryFromDb(Long brandId, ProductSortType sort, int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size, toSort(sort));

        Page<Product> result;
        if (brandId != null) {
            result = productJpaRepository.findAllByBrandIdAndDeletedAtIsNull(brandId, pageRequest);
        } else {
            result = productJpaRepository.findAllByDeletedAtIsNull(pageRequest);
        }

        return new PageResult<>(
            result.getContent().stream().map(ProductReadModel::from).toList(),
            result.getNumber(),
            result.getSize(),
            result.getTotalElements(),
            result.getTotalPages()
        );
    }

    private Sort toSort(ProductSortType sortType) {
        Sort primary = switch (sortType) {
            case PRICE_ASC -> Sort.by(Sort.Direction.ASC, "price");
            case LIKES_DESC -> Sort.by(Sort.Direction.DESC, "likeCount");
            case LATEST -> Sort.by(Sort.Direction.DESC, "createdAt");
        };
        return primary.and(Sort.by(Sort.Direction.DESC, "id"));
    }
}
