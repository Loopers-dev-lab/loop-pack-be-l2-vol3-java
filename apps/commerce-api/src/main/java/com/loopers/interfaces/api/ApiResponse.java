package com.loopers.interfaces.api;

/**
 * 표준 API 응답 래퍼
 *
 * 모든 API 응답은 이 포맷으로 통일한다.
 * - meta: 결과 상태(SUCCESS/FAIL), 에러 코드, 에러 메시지
 * - data: 응답 본문 (실패 시 null)
 */
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
}
