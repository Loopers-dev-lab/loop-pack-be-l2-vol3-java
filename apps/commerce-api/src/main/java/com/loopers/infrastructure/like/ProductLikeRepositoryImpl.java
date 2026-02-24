package com.loopers.infrastructure.like;

import com.loopers.domain.like.ProductLike;
import com.loopers.domain.like.ProductLikeRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * ProductLikeRepository 구현체
 * Mapper를 활용하여 Domain ↔ Entity 변환
 */
@Repository
public class ProductLikeRepositoryImpl implements ProductLikeRepository {

    private final ProductLikeJpaRepository productLikeJpaRepository;
    private final ProductLikeMapper productLikeMapper;

    public ProductLikeRepositoryImpl(ProductLikeJpaRepository productLikeJpaRepository,
                                      ProductLikeMapper productLikeMapper) {
        this.productLikeJpaRepository = productLikeJpaRepository;
        this.productLikeMapper = productLikeMapper;
    }

    @Override
    public ProductLike save(ProductLike productLike) {
        // Domain → Entity
        ProductLikeEntity entity = productLikeMapper.toEntity(productLike);

        // JPA save
        ProductLikeEntity saved = productLikeJpaRepository.save(entity);

        // Entity → Domain
        return productLikeMapper.toDomain(saved);
    }

    @Override
    public Optional<ProductLike> findByUserIdAndProductId(Long userId, Long productId) {
        return productLikeJpaRepository.findByUserIdAndProductId(userId, productId)
                .map(productLikeMapper::toDomain);  // Entity → Domain
    }

    @Override
    public boolean existsByUserIdAndProductId(Long userId, Long productId) {
        return productLikeJpaRepository.existsByUserIdAndProductId(userId, productId);
    }

    @Override
    public void delete(ProductLike productLike) {
        // Domain → Entity
        ProductLikeEntity entity = productLikeMapper.toEntity(productLike);
        productLikeJpaRepository.delete(entity);
    }

    @Override
    public List<ProductLike> findActiveByUserId(Long userId, int page, int size) {
        return productLikeJpaRepository.findActiveByUserId(userId, PageRequest.of(page, size)).stream()
                .map(productLikeMapper::toDomain)  // Entity → Domain
                .collect(Collectors.toList());
    }

    @Override
    public long countActiveByUserId(Long userId) {
        return productLikeJpaRepository.countActiveByUserId(userId);
    }
}

