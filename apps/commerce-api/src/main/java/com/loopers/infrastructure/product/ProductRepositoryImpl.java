package com.loopers.infrastructure.product;

import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.product.ProductSortCondition;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class ProductRepositoryImpl implements ProductRepository {
    private final ProductJpaRepository productJpaRepository;

    @Override
    public Product save(Product product) {
        return productJpaRepository.save(product);
    }

    @Override
    public Optional<Product> findById(Long id) {
        return productJpaRepository.findByIdAndDeletedFalse(id);
    }

    @Override
    public List<Product> findAll(ProductSortCondition condition) {
        if (condition == null) {
            return productJpaRepository.findByDeletedFalse(Sort.unsorted());
        }

        if (condition == ProductSortCondition.LIKES_DESC) {
            return productJpaRepository.findAllOrderByLikesDescAndDeletedFalse();
        }

        Sort sort = switch (condition) {
            case LATEST -> Sort.by(Sort.Direction.DESC, "createdAt");
            case PRICE_ASC -> Sort.by(Sort.Direction.ASC, "basePrice");
            default -> Sort.by(Sort.Direction.DESC, "createdAt");
        };

        return productJpaRepository.findByDeletedFalse(sort);
    }

    @Override
    public List<Product> findByBrandId(Long brandId) {
        return productJpaRepository.findByBrandIdAndDeletedFalse(brandId);
    }

    @Override
    public List<Product> findByIdIn(List<Long> productIds) {
        return productJpaRepository.findByIdInAndDeletedFalse(productIds);
    }
}
