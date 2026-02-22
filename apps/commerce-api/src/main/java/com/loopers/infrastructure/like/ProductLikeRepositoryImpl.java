package com.loopers.infrastructure.like;

import com.loopers.domain.like.ProductLike;
import com.loopers.domain.like.ProductLikeRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class ProductLikeRepositoryImpl implements ProductLikeRepository {

    private final ProductLikeJpaRepository productLikeJpaRepository;

    public ProductLikeRepositoryImpl(ProductLikeJpaRepository productLikeJpaRepository) {
        this.productLikeJpaRepository = productLikeJpaRepository;
    }

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
    public List<ProductLike> findActiveByUserId(Long userId, int page, int size) {
        return productLikeJpaRepository.findActiveByUserId(userId, PageRequest.of(page, size));
    }

    @Override
    public long countActiveByUserId(Long userId) {
        return productLikeJpaRepository.countActiveByUserId(userId);
    }
}
