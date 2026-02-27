package com.loopers.infrastructure.like;

import com.loopers.domain.like.Like;
import com.loopers.domain.like.LikeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class LikeRepositoryImpl implements LikeRepository {

    private final LikeJpaRepository likeJpaRepository;

    @Override
    public Like save(Like like) {
        LikeEntity entity = LikeEntity.from(like);
        return likeJpaRepository.save(entity).toDomain();
    }

    @Override
    public boolean existsByMemberIdAndProductId(String memberId, Long productId) {
        return likeJpaRepository.existsByMemberIdAndProductId(memberId, productId);
    }

    @Override
    public void deleteByMemberIdAndProductId(String memberId, Long productId) {
        likeJpaRepository.deleteByMemberIdAndProductId(memberId, productId);
    }

    @Override
    public void deleteByProductIds(List<Long> productIds) {
        likeJpaRepository.deleteByProductIdIn(productIds);
    }

    @Override
    public Page<Like> findByMemberId(String memberId, Pageable pageable) {
        return likeJpaRepository.findByMemberIdOrderByCreatedAtDesc(memberId, pageable)
                .map(LikeEntity::toDomain);
    }
}
