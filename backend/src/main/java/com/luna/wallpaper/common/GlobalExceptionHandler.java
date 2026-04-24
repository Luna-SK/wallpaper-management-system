package com.luna.wallpaper.common;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import jakarta.validation.ConstraintViolationException;

@RestControllerAdvice
class GlobalExceptionHandler {

	@ExceptionHandler(IllegalArgumentException.class)
	ResponseEntity<ApiResponse<Void>> handleIllegalArgument(IllegalArgumentException exception) {
		return badRequest(exception.getMessage());
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException exception) {
		String message = exception.getBindingResult().getFieldErrors().stream()
				.findFirst()
				.map(error -> "%s %s".formatted(error.getField(), error.getDefaultMessage()))
				.orElse("request validation failed");
		return badRequest(message);
	}

	@ExceptionHandler(ConstraintViolationException.class)
	ResponseEntity<ApiResponse<Void>> handleConstraintViolation(ConstraintViolationException exception) {
		return badRequest(exception.getMessage());
	}

	private ResponseEntity<ApiResponse<Void>> badRequest(String message) {
		return ResponseEntity.status(HttpStatus.BAD_REQUEST)
				.body(ApiResponse.error("VALIDATION_ERROR", message));
	}
}
