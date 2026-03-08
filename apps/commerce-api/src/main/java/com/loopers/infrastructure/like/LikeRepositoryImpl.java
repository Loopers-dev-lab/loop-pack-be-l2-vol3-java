package com.loopers.infrastructure.like;

import com.loopers.domain.like.Like;
import com.loopers.domain.like.LikeRepository;
import com.loopers.infrastructure.support.ConstraintViolationHelper;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@Component
public class LikeRepositoryImpl implements LikeRepository {

    private final LikeJpaRepository likeJpaRepository;

    @Override
    public Like save(Like like) {
        try {
            return likeJpaRepository.saveAndFlush(like);
        } catch (DataIntegrityViolationException e) {
            if (ConstraintViolationHelper.isUniqueViolation(e, "uk_likes_user_product")) {
                throw new CoreException(ErrorType.CONFLICT, "이미 좋아요한 상품입니다.");
            }
            throw e;
        }
    }

    @Override
    public void delete(Like like) {
        likeJpaRepository.delete(like);
    }

    @Override
    public Optional<Like> findByUserIdAndProductId(Long userId, Long productId) {
        return likeJpaRepository.findByUserIdAndProductId(userId, productId);
    }

    @Override
    public List<Like> findAllByUserId(Long userId) {
        return likeJpaRepository.findAllByUserId(userId);
    }
}
