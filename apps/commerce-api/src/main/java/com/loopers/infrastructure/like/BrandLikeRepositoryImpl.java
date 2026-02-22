package com.loopers.infrastructure.like;

import com.loopers.domain.like.BrandLike;
import com.loopers.domain.like.BrandLikeRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class BrandLikeRepositoryImpl implements BrandLikeRepository {

    private final BrandLikeJpaRepository brandLikeJpaRepository;

    public BrandLikeRepositoryImpl(BrandLikeJpaRepository brandLikeJpaRepository) {
        this.brandLikeJpaRepository = brandLikeJpaRepository;
    }

    @Override
    public BrandLike save(BrandLike brandLike) {
        return brandLikeJpaRepository.save(brandLike);
    }

    @Override
    public Optional<BrandLike> findByUserIdAndBrandId(Long userId, Long brandId) {
        return brandLikeJpaRepository.findByUserIdAndBrandId(userId, brandId);
    }

    @Override
    public boolean existsByUserIdAndBrandId(Long userId, Long brandId) {
        return brandLikeJpaRepository.existsByUserIdAndBrandId(userId, brandId);
    }

    @Override
    public void delete(BrandLike brandLike) {
        brandLikeJpaRepository.delete(brandLike);
    }

    @Override
    public List<BrandLike> findActiveByUserId(Long userId, int page, int size) {
        return brandLikeJpaRepository.findActiveByUserId(userId, PageRequest.of(page, size));
    }

    @Override
    public long countActiveByUserId(Long userId) {
        return brandLikeJpaRepository.countActiveByUserId(userId);
    }
}
