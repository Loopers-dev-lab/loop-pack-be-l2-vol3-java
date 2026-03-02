package com.loopers.infrastructure.productlike;

import com.loopers.domain.productlike.ProductLike;
import com.loopers.domain.productlike.ProductLikeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class ProductLikeRepositoryImpl implements ProductLikeRepository {

    private final ProductLikeJpaRepository productLikeJpaRepository;

    @Override
    public ProductLike save(ProductLike productLike) {
        return productLikeJpaRepository.save(productLike);
    }

    @Override
    public Optional<ProductLike> findByUserIdAndProductId(Long userId, Long productId) {
        return productLikeJpaRepository.findByUserIdAndProductId(userId, productId);
    }

    @Override
    public boolean existsByUserIdAndProductId(Long userId, Long productId) {
        return productLikeJpaRepository.existsByUserIdAndProductId(userId, productId);
    }

    @Override
    public void delete(ProductLike productLike) {
        productLikeJpaRepository.delete(productLike);
    }

    @Override
    public void deleteByProductId(Long productId) {
        productLikeJpaRepository.deleteByProductId(productId);
    }

    @Override
    public Page<ProductLike> findAllByUserId(Long userId, Pageable pageable) {
        return productLikeJpaRepository.findAllByUserId(userId, pageable);
    }

    @Override
    public List<ProductLike> findAllByProductId(Long productId) {
        return productLikeJpaRepository.findAllByProductId(productId);
    }

    @Override
    public void deleteByProductIds(List<Long> productIds) {
        productLikeJpaRepository.deleteAllByProductIdIn(productIds);
    }
}
