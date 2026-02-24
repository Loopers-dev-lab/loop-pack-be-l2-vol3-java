package com.loopers.application.like;

import com.loopers.domain.like.Like;
import com.loopers.domain.like.LikeRepository;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LikeFacade {

    private final LikeRepository likeRepository;
    private final ProductRepository productRepository;

    @Transactional
    public void addLike(Long memberId, Long productId) {
        Product product = productRepository.findById(productId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "상품을 찾을 수 없습니다."));

        if (likeRepository.existsByMemberIdAndProductId(memberId, productId)) {
            return;
        }

        likeRepository.save(new Like(memberId, productId));
        product.incrementLikeCount();
    }

    @Transactional
    public void removeLike(Long memberId, Long productId) {
        Optional<Like> likeOpt = likeRepository.findByMemberIdAndProductId(memberId, productId);
        if (likeOpt.isEmpty()) {
            return;
        }

        likeRepository.delete(likeOpt.get());

        Product product = productRepository.findById(productId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "상품을 찾을 수 없습니다."));
        product.decrementLikeCount();
    }

    public List<Like> getLikesByMemberId(Long memberId) {
        return likeRepository.findAllByMemberId(memberId);
    }
}
