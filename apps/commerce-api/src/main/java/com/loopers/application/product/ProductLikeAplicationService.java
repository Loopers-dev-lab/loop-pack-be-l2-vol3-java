package com.loopers.application.product;

import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductLikeAplicationService {

    private final ProductRepository productRepository;

    @Transactional(readOnly = true)
    public void validateLikeable(Long productId) {
        if (productRepository.findById(productId).isPresent()) {
            return;
        }
        if (productRepository.findByIdIncludingDeleted(productId).isPresent()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "삭제된 상품은 좋아요할 수 없습니다.");
        }
        throw new CoreException(ErrorType.NOT_FOUND, "상품을 찾을 수 없습니다.");
    }

    @Transactional(readOnly = true)
    public void validateCancelable(Long productId) {
        productRepository.findById(productId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "상품을 찾을 수 없습니다."));
    }

    @Transactional
    public void increaseLikeCount(Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "상품을 찾을 수 없습니다."));
        productRepository.save(product.increaseLikeCount());
    }

    @Transactional
    public void decreaseLikeCount(Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "상품을 찾을 수 없습니다."));
        productRepository.save(product.decreaseLikeCount());
    }

    @Transactional(readOnly = true)
    public Page<Product> getMyLikedProducts(Page<Long> likedProductIds, Pageable pageable) {
        List<Product> products = likedProductIds.getContent().stream()
                .map(productRepository::findById)
                .flatMap(java.util.Optional::stream)
                .toList();
        return new PageImpl<>(products, pageable, products.size());
    }
}
