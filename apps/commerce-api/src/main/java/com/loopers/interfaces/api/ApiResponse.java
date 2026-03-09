package com.loopers.interfaces.api;

/**
 * 통합 API 응답 래퍼.
 *
 * <p>모든 REST API 응답을 일관된 형식으로 감싸는 제네릭 레코드이다.
 * 메타데이터({@link Metadata})와 실제 데이터를 포함한다.</p>
 *
 * @param meta 응답 메타데이터 (성공/실패 여부, 에러 코드, 메시지)
 * @param data 응답 데이터
 * @param <T> 응답 데이터 타입
 */
public record ApiResponse<T>(Metadata meta, T data) {
    /**
     * 응답 메타데이터.
     *
     * <p>API 호출의 성공/실패 상태, 에러 코드 및 메시지를 담는다.</p>
     *
     * @param result 처리 결과 (SUCCESS 또는 FAIL)
     * @param errorCode 에러 코드 (실패 시)
     * @param message 에러 메시지 (실패 시)
     */
    public record Metadata(Result result, String errorCode, String message) {
        /**
         * API 처리 결과 열거형.
         */
        public enum Result {
            SUCCESS, FAIL
        }

        /**
         * 성공 메타데이터를 생성한다.
         *
         * @return 에러 코드와 메시지가 없는 SUCCESS 메타데이터
         */
        public static Metadata success() {
            return new Metadata(Result.SUCCESS, null, null);
        }

        /**
         * 실패 메타데이터를 생성한다.
         *
         * @param errorCode 에러 코드
         * @param errorMessage 에러 메시지
         * @return 에러 정보를 포함한 FAIL 메타데이터
         */
        public static Metadata fail(String errorCode, String errorMessage) {
            return new Metadata(Result.FAIL, errorCode, errorMessage);
        }
    }

    /**
     * 데이터 없는 성공 응답을 생성한다.
     *
     * @return 데이터가 null인 성공 ApiResponse
     */
    public static ApiResponse<Object> success() {
        return new ApiResponse<>(Metadata.success(), null);
    }

    /**
     * 데이터를 포함한 성공 응답을 생성한다.
     *
     * @param data 응답 데이터
     * @param <T> 응답 데이터 타입
     * @return 데이터를 포함한 성공 ApiResponse
     */
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(Metadata.success(), data);
    }

    /**
     * 실패 응답을 생성한다.
     *
     * @param errorCode 에러 코드
     * @param errorMessage 에러 메시지
     * @return 에러 정보를 포함한 실패 ApiResponse
     */
    public static ApiResponse<Object> fail(String errorCode, String errorMessage) {
        return new ApiResponse<>(
            Metadata.fail(errorCode, errorMessage),
            null
        );
    }
}
