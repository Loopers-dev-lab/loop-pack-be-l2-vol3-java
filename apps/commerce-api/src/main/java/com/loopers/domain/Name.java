package com.loopers.domain;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

public class Name {
    private final static int MIN_LENGTH = 2;
    private final static int MAX_LENGTH = 10;
    private final String name;

    public Name(String name) {
        validate(name);
        this.name = name;
    }

    private void validate(String name) {
        if (name == null || name.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "이름은 비어있을 수 없습니다.");
        }
        if(name.length() < MIN_LENGTH ||  name.length() > MAX_LENGTH){
            throw new CoreException(ErrorType.BAD_REQUEST, "유효하지 않은 이름 길이입니다.");
        }
    }
}
