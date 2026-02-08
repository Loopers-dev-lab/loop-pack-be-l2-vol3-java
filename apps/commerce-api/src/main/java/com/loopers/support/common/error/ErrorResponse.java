package com.loopers.support.common.error;

public record ErrorResponse(
	String code,
	String message
) {
	public static ErrorResponse from(ErrorType errorType) {
		return new ErrorResponse(errorType.getCode(), errorType.getMessage());
	}

	public static ErrorResponse of(String code, String message) {
		return new ErrorResponse(code, message);
	}
}
