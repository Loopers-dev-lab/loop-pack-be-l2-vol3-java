package com.loopers.application.like;

import com.loopers.domain.like.Like;
import com.loopers.domain.like.LikeRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@RequiredArgsConstructor
@Service
public class LikeApplicationService {
    private final LikeRepository likeRepository;

    @Transactional
    public LikeInfo register(Long userId, Long productId) {
        likeRepository.findByUserIdAndProductId(userId, productId).ifPresent(like -> {
            throw new CoreException(ErrorType.ALREADY_LIKED, "이미 좋아요한 상품입니다.");
        });

        Like like = Like.create(userId, productId);
        return LikeInfo.from(likeRepository.save(like));
    }

    @Transactional
    public boolean cancel(Long userId, Long productId) {
        return likeRepository.deleteByUserIdAndProductId(userId, productId) > 0;
    }

    @Transactional(readOnly = true)
    public List<LikeInfo> getLikesByUserId(Long userId) {
        return likeRepository.findByUserId(userId)
                             .stream()
                             .map(LikeInfo::from)
                             .toList();
    }

    @Transactional
    public void deleteAllByProductIds(List<Long> productIds) {
        if (productIds.isEmpty()) {
            return;
        }

        likeRepository.deleteAllByProductIdIn(productIds);
    }
}
