package com.loopers.infrastructure.like;

import com.loopers.application.like.LikeProductReadPort;
import com.loopers.domain.model.like.Like;
import com.loopers.domain.model.user.UserId;
import com.loopers.domain.repository.LikeRepository;
import com.loopers.infrastructure.brand.BrandJpaEntity;
import com.loopers.infrastructure.product.ProductJpaEntity;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class LikeRepositoryImpl implements LikeRepository, LikeProductReadPort {

    private final LikeJpaRepository likeJpaRepository;

    public LikeRepositoryImpl(LikeJpaRepository likeJpaRepository) {
        this.likeJpaRepository = likeJpaRepository;
    }

    @Override
    public Like save(Like like) {
        LikeJpaEntity entity = toEntity(like);
        LikeJpaEntity saved = likeJpaRepository.save(entity);
        return toDomain(saved);
    }

    @Override
    public Optional<Like> findByUserIdAndProductId(UserId userId, Long productId) {
        return likeJpaRepository.findByUserIdAndProductId(userId.getValue(), productId)
                .map(this::toDomain);
    }

    @Override
    public void deleteByUserIdAndProductId(UserId userId, Long productId) {
        likeJpaRepository.deleteByUserIdAndProductId(userId.getValue(), productId);
    }

    @Override
    public boolean existsByUserIdAndProductId(UserId userId, Long productId) {
        return likeJpaRepository.existsByUserIdAndProductId(userId.getValue(), productId);
    }

    @Override
    public List<Like> findAllByUserId(UserId userId) {
        return likeJpaRepository.findAllByUserId(userId.getValue()).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public List<LikeProductView> findLikedProductsByUserId(UserId userId) {
        return likeJpaRepository.findAllWithProductByUserId(userId.getValue()).stream()
                .map(this::toLikeProductView)
                .toList();
    }

    private LikeProductView toLikeProductView(Object[] row) {
        LikeJpaEntity like = (LikeJpaEntity) row[0];
        ProductJpaEntity product = (ProductJpaEntity) row[1];
        BrandJpaEntity brand = (BrandJpaEntity) row[2];
        return new LikeProductView(
                product.getId(),
                product.getName(),
                product.getPrice(),
                product.getSalePrice(),
                product.getStockQuantity(),
                brand.getName(),
                like.getCreatedAt()
        );
    }

    private LikeJpaEntity toEntity(Like like) {
        return new LikeJpaEntity(
                like.getId(),
                like.getUserId().getValue(),
                like.getProductId(),
                like.getCreatedAt()
        );
    }

    private Like toDomain(LikeJpaEntity entity) {
        return Like.reconstitute(
                entity.getId(),
                UserId.of(entity.getUserId()),
                entity.getProductId(),
                entity.getCreatedAt()
        );
    }
}
