package com.loopers.domain.favorite.model;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.Getter;

@Getter
public class Favorite {

    private Long id;
    private Long memberId;
    private Long productId;

    private Favorite(Long memberId, Long productId) {
        if (memberId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "회원 정보는 필수입니다.");
        }
        if (productId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "상품 정보는 필수입니다.");
        }
        this.memberId = memberId;
        this.productId = productId;
    }

    public static Favorite create(Long memberId, Long productId) {
        return new Favorite(memberId, productId);
    }

    public static Favorite reconstruct(Long id, Long memberId, Long productId) {
        Favorite favorite = new Favorite(memberId, productId);
        favorite.id = id;
        return favorite;
    }
}
