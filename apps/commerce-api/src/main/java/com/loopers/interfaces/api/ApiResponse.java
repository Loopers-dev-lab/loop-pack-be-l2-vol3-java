package com.loopers.interfaces.api;

import java.util.List;

public record ApiResponse<T>(Metadata meta, T data) {
    public record Metadata(Result result, String errorCode, String message) {
        public enum Result {
            SUCCESS, FAIL
        }

        public static Metadata success() {
            return new Metadata(Result.SUCCESS, null, null);
        }

        public static Metadata fail(String errorCode, String errorMessage) {
            return new Metadata(Result.FAIL, errorCode, errorMessage);
        }
    }

    public record FieldError(String field, Object value, String reason) {}

    public static ApiResponse<Object> success() {
        return new ApiResponse<>(Metadata.success(), null);
    }

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(Metadata.success(), data);
    }

    public static ApiResponse<Object> fail(String errorCode, String errorMessage) {
        return new ApiResponse<>(
            Metadata.fail(errorCode, errorMessage),
            null
        );
    }

    public static ApiResponse<List<FieldError>> failValidation(
            String errorCode, String errorMessage, List<FieldError> fieldErrors) {
        return new ApiResponse<>(Metadata.fail(errorCode, errorMessage), fieldErrors);
    }
}
