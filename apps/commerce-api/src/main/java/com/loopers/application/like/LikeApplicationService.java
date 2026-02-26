package com.loopers.application.like;

import com.loopers.domain.like.Like;
import com.loopers.domain.like.LikeRepository;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.member.Member;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Service
public class LikeApplicationService {

    private final ProductRepository productRepository;
    private final LikeRepository likeRepository;

    public LikeApplicationService(ProductRepository productRepository, LikeRepository likeRepository) {
        this.productRepository = productRepository;
        this.likeRepository = likeRepository;
    }

    @Transactional
    public void register(Long productId, Member member) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> findProductByIdIncludingDeletedOrThrow(productId));

        if (likeRepository.existsByMemberIdAndProductId(member.id().value(), productId)) {
            throw new CoreException(ErrorType.CONFLICT, "이미 좋아요를 누른 상품입니다.");
        }

        try {
            likeRepository.save(new Like(member.id().value(), productId));
        } catch (DataIntegrityViolationException e) {
            throw new CoreException(ErrorType.CONFLICT, "이미 좋아요를 누른 상품입니다.");
        }

        productRepository.save(product.increaseLikeCount());
    }

    @Transactional
    public void cancel(Long productId, Member member) {
        String memberId = member.id().value();
        if (!likeRepository.existsByMemberIdAndProductId(memberId, productId)) {
            throw new CoreException(ErrorType.NOT_FOUND, "좋아요한 상품이 아닙니다.");
        }

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "상품을 찾을 수 없습니다."));

        likeRepository.deleteByMemberIdAndProductId(memberId, productId);
        productRepository.save(product.decreaseLikeCount());
    }

    public Page<Product> getMyLikes(String memberId, Pageable pageable) {
        List<Product> products = likeRepository.findByMemberId(memberId, pageable)
                .map(like -> productRepository.findById(like.productId()).orElse(null))
                .stream()
                .filter(Objects::nonNull)
                .toList();

        return new PageImpl<>(products, pageable, products.size());
    }

    private CoreException findProductByIdIncludingDeletedOrThrow(Long productId) {
        return productRepository.findByIdIncludingDeleted(productId)
                .map(product -> new CoreException(ErrorType.BAD_REQUEST, "삭제된 상품은 좋아요할 수 없습니다."))
                .orElse(new CoreException(ErrorType.NOT_FOUND, "상품을 찾을 수 없습니다."));
    }
}
