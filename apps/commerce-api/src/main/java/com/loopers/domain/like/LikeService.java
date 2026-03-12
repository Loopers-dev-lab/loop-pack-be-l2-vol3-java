package com.loopers.domain.like;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@RequiredArgsConstructor
@Component
public class LikeService {

    private final LikeRepository likeRepository;

    /**
     * 좋아요 등록 (BR-L01: 중복 방지)
     * exists 체크와 save 사이의 TOCTOU 경쟁 조건에서 DB UNIQUE 제약이 위반될 수 있다.
     * saveAndFlush()로 INSERT를 즉시 flush하여 이 메서드 내에서 예외를 잡고 도메인 에러로 변환한다.
     * 주의: saveAndFlush()는 영속성 컨텍스트 전체를 flush하므로,
     *       호출 이전에 다른 dirty 엔티티가 없는 상태여야 의도치 않은 flush가 발생하지 않는다.
     */
    @Transactional
    public Like create(Long userId, Long productId) {
        if (likeRepository.existsByUserIdAndProductId(userId, productId)) {
            throw new CoreException(ErrorType.CONFLICT, "이미 좋아요한 상품입니다.");
        }
        try {
            return likeRepository.saveAndFlush(new Like(userId, productId));
        } catch (DataIntegrityViolationException e) {
            // exists 체크를 동시에 통과한 경우 UNIQUE 제약 위반 → CONFLICT 에러로 변환하여 반환
            throw new CoreException(ErrorType.CONFLICT, "이미 좋아요한 상품입니다.");
        }
    }

    // 좋아요 취소 (BR-L02: 좋아요하지 않은 상품은 취소 불가)
    @Transactional
    public void delete(Long userId, Long productId) {
        if (!likeRepository.existsByUserIdAndProductId(userId, productId)) {
            throw new CoreException(ErrorType.NOT_FOUND, "좋아요 상태가 아닙니다.");
        }
        likeRepository.deleteByUserIdAndProductId(userId, productId);
    }

    // 좋아요 목록 조회 (BR-L03: 자신의 좋아요 목록만 조회)
    @Transactional(readOnly = true)
    public List<Like> findAllByUserId(Long userId) {
        return likeRepository.findAllByUserId(userId);
    }

    // 상품 삭제 시 cascade hard delete (US-P07)
    @Transactional
    public void deleteAllByProductId(Long productId) {
        likeRepository.deleteAllByProductId(productId);
    }

    // 브랜드 삭제 시 cascade hard delete (US-B06)
    @Transactional
    public void deleteAllByProductIds(List<Long> productIds) {
        if (productIds.isEmpty()) {
            return;
        }
        likeRepository.deleteAllByProductIds(productIds);
    }
}
