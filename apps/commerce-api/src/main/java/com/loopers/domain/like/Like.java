package com.loopers.domain.like;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorMessage;
import com.loopers.support.error.ErrorType;

/**
 *  좋아요 도메인 객체
 */
public class Like {

    private Long id;
    private Long refProductId;
    private Long refUserId;

    private Like(Long id, Long refProductId, Long refUserId) {
        this.id = id;
        this.refProductId = refProductId;
        this.refUserId = refUserId;
    }

    public static Like create(Long id, Long refProductId, Long refUserId) {
        validateRefId(refProductId, refUserId);

        return new Like(id, refProductId, refUserId);
    }

    private static void validateRefId(Long refProductId, Long refUserId) {
        if (refProductId == null || refProductId < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, ErrorMessage.Like.PRODUCT_ID_INVALID);
        }

        if (refUserId == null || refUserId < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, ErrorMessage.Like.USER_ID_INVALID);
        }
    }

    public Long getRefProductId() {
        return refProductId;
    }

    public Long getRefUserId() {
        return refUserId;
    }

}
