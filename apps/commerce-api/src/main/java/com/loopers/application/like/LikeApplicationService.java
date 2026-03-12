package com.loopers.application.like;

import com.loopers.domain.like.Like;
import com.loopers.domain.like.LikeRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class LikeApplicationService {

    private final LikeRepository likeRepository;

    public LikeApplicationService(LikeRepository likeRepository) {
        this.likeRepository = likeRepository;
    }

    @Transactional
    public void register(String memberId, UUID productId) {
        if (likeRepository.existsByMemberIdAndProductId(memberId, productId)) {
            throw new CoreException(ErrorType.CONFLICT, "이미 좋아요를 누른 상품입니다.");
        }

        try {
            likeRepository.save(new Like(memberId, productId));
        } catch (DataIntegrityViolationException e) {
            throw new CoreException(ErrorType.CONFLICT, "이미 좋아요를 누른 상품입니다.");
        }
    }

    @Transactional
    public void cancel(String memberId, UUID productId) {
        if (!likeRepository.existsByMemberIdAndProductId(memberId, productId)) {
            throw new CoreException(ErrorType.NOT_FOUND, "좋아요한 상품이 아닙니다.");
        }
        likeRepository.deleteByMemberIdAndProductId(memberId, productId);
    }

    @Transactional(readOnly = true)
    public void assertLiked(String memberId, UUID productId) {
        if (!likeRepository.existsByMemberIdAndProductId(memberId, productId)) {
            throw new CoreException(ErrorType.NOT_FOUND, "좋아요한 상품이 아닙니다.");
        }
    }

    @Transactional(readOnly = true)
    public Page<UUID> getMyLikeProductIds(String memberId, Pageable pageable) {
        return likeRepository.findByMemberId(memberId, pageable)
                .map(Like::productId);
    }

    @Transactional
    public void deleteByProductIds(List<UUID> productIds) {
        if (productIds.isEmpty()) {
            return;
        }
        likeRepository.deleteByProductIds(productIds);
    }
}
