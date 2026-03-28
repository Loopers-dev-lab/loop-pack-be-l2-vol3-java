package com.loopers.domain.like;

import com.loopers.domain.common.vo.RefMemberId;
import com.loopers.domain.common.vo.RefProductId;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.product.vo.ProductId;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class LikeService {

    private final LikeRepository likeRepository;
    private final ProductRepository productRepository;

    @Transactional
    public LikeActionResult addLike(Long memberId, String productId) {
        ProductModel product = productRepository.findByProductId(new ProductId(productId))
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "해당 ID의 상품이 존재하지 않습니다."));

        RefMemberId refMemberId = new RefMemberId(memberId);
        RefProductId refProductId = new RefProductId(product.getId());

        return likeRepository.findByRefMemberIdAndRefProductId(refMemberId, refProductId)
                .map(existingLike -> {
                    if (existingLike.getDeletedAt() != null) {
                        int restored = likeRepository.restoreIfDeleted(existingLike.getId());
                        if (restored > 0) {
                            existingLike.restore();
                            return new LikeActionResult(existingLike, true);
                        }
                    }
                    return new LikeActionResult(existingLike, false);
                })
                .orElseGet(() -> {
                    LikeModel saved = likeRepository.save(LikeModel.create(memberId, product.getId()));
                    return new LikeActionResult(saved, true);
                });
    }

    @Transactional
    public Optional<LikeModel> removeLike(Long memberId, String productId) {
        ProductModel product = productRepository.findByProductId(new ProductId(productId))
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "해당 ID의 상품이 존재하지 않습니다."));

        RefMemberId refMemberId = new RefMemberId(memberId);
        RefProductId refProductId = new RefProductId(product.getId());

        return likeRepository.findByRefMemberIdAndRefProductId(refMemberId, refProductId)
                .flatMap(like -> {
                    if (like.getDeletedAt() == null) {
                        int deleted = likeRepository.softDeleteIfActive(like.getId());
                        if (deleted > 0) {
                            return Optional.of(like);
                        }
                    }
                    return Optional.empty();
                });
    }
}
