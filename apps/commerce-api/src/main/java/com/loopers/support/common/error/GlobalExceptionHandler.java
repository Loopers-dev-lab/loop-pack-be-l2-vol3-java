package com.loopers.support.common.error;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler(CoreException.class)
	public ResponseEntity<ErrorResponse> handleCoreException(CoreException e) {
		ErrorType errorType = e.getErrorType();
		ErrorResponse response = ErrorResponse.from(errorType);
		return ResponseEntity.status(errorType.getStatus()).body(response);
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException e) {
		String message = e.getBindingResult().getFieldErrors().stream()
			.findFirst()
			.map(error -> error.getField() + ": " + error.getDefaultMessage())
			.orElse("Validation failed");

		ErrorResponse response = ErrorResponse.of(ErrorType.BAD_REQUEST.getCode(), message);
		return ResponseEntity.status(ErrorType.BAD_REQUEST.getStatus()).body(response);
	}
}
