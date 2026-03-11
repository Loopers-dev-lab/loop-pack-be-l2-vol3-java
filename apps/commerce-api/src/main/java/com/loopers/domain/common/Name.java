package com.loopers.domain.common;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorMessage;
import com.loopers.support.error.ErrorType;

/**
 * 이름 VO
 */
public record Name(String value) {

    public Name {
        if (value == null || value.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, ErrorMessage.Name.NAME_REQUIRED);
        }
    }
}
