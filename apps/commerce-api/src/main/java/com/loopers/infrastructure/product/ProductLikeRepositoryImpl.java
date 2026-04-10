package com.loopers.infrastructure.product;

import com.loopers.domain.product.ProductLikeModel;
import com.loopers.domain.product.ProductLikeRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ProductLikeRepositoryImpl implements ProductLikeRepository {
    private final ProductLikeJpaRepository productLikeJpaRepository;
    @Override
    public ProductLikeModel save(ProductLikeModel productLike) {
        return productLikeJpaRepository.save(productLike);
    }

    @Override
    public Optional<ProductLikeModel> findByUserIdAndProductId(Long userId, Long productId) {
        return productLikeJpaRepository.findByUserIdAndProductId(userId, productId);
    }

    @Override
    public void delete(ProductLikeModel productLike) {
        productLikeJpaRepository.delete(productLike);
    }

    @Override
    public List<ProductLikeModel> findAllByUserId(Long userId) {
        return productLikeJpaRepository.findAllByUserId(userId);
    }

    @Override
    public long countByProductId(Long productId) {
        return productLikeJpaRepository.countByProductId(productId);
    }

    @Override
    public Map<Long, Long> countByProductIds(List<Long> productIds) {
        return productLikeJpaRepository.countByProductIdIn(productIds).stream()
                .collect(java.util.stream.Collectors.toMap(
                        row -> (Long) row[0],
                        row -> (Long) row[1]));
    }

    @Override
    public Map<Long, Long> countByProductIdsWithModulo(int divisor, int remainder) {
        return productLikeJpaRepository.countByProductIdModulo(divisor, remainder).stream()
                .collect(java.util.stream.Collectors.toMap(
                        row -> (Long) row[0],
                        row -> (Long) row[1]));
    }
}
