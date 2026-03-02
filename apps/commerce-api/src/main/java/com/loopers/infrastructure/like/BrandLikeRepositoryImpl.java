package com.loopers.infrastructure.like;

import com.loopers.domain.like.BrandLike;
import com.loopers.domain.like.BrandLikeRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * BrandLikeRepository 구현체
 * Mapper를 활용하여 Domain ↔ Entity 변환
 */
@Repository
public class BrandLikeRepositoryImpl implements BrandLikeRepository {

    private final BrandLikeJpaRepository brandLikeJpaRepository;
    private final BrandLikeMapper brandLikeMapper;

    public BrandLikeRepositoryImpl(BrandLikeJpaRepository brandLikeJpaRepository,
                                    BrandLikeMapper brandLikeMapper) {
        this.brandLikeJpaRepository = brandLikeJpaRepository;
        this.brandLikeMapper = brandLikeMapper;
    }

    @Override
    public BrandLike save(BrandLike brandLike) {
        // Domain → Entity
        BrandLikeEntity entity = brandLikeMapper.toEntity(brandLike);

        // JPA save
        BrandLikeEntity saved = brandLikeJpaRepository.save(entity);

        // Entity → Domain
        return brandLikeMapper.toDomain(saved);
    }

    @Override
    public Optional<BrandLike> findByUserIdAndBrandId(Long userId, Long brandId) {
        return brandLikeJpaRepository.findByUserIdAndBrandId(userId, brandId)
                .map(brandLikeMapper::toDomain);  // Entity → Domain
    }

    @Override
    public boolean existsByUserIdAndBrandId(Long userId, Long brandId) {
        return brandLikeJpaRepository.existsByUserIdAndBrandId(userId, brandId);
    }

    @Override
    public void delete(BrandLike brandLike) {
        // Domain → Entity
        BrandLikeEntity entity = brandLikeMapper.toEntity(brandLike);
        brandLikeJpaRepository.delete(entity);
    }

    @Override
    public List<BrandLike> findActiveByUserId(Long userId, int page, int size) {
        return brandLikeJpaRepository.findActiveByUserId(userId, PageRequest.of(page, size)).stream()
                .map(brandLikeMapper::toDomain)  // Entity → Domain
                .collect(Collectors.toList());
    }

    @Override
    public long countActiveByUserId(Long userId) {
        return brandLikeJpaRepository.countActiveByUserId(userId);
    }
}

