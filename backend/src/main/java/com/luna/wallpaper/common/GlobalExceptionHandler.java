package com.luna.wallpaper.common;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.luna.wallpaper.taxonomy.TaxonomyReferenceException;
import com.luna.wallpaper.taxonomy.TaxonomyDtos.ReferenceImpact;

import jakarta.validation.ConstraintViolationException;

@RestControllerAdvice
class GlobalExceptionHandler {

	@ExceptionHandler(IllegalArgumentException.class)
	ResponseEntity<ApiResponse<Void>> handleIllegalArgument(IllegalArgumentException exception) {
		return badRequest(exception.getMessage());
	}

	@ExceptionHandler(TaxonomyReferenceException.class)
	ResponseEntity<ApiResponse<ReferenceImpact>> handleTaxonomyReference(TaxonomyReferenceException exception) {
		return ResponseEntity.status(HttpStatus.CONFLICT)
				.body(new ApiResponse<>("REFERENCE_EXISTS", exception.getMessage(), exception.impact(), null));
	}

	@ExceptionHandler(AuthenticationException.class)
	ResponseEntity<ApiResponse<Void>> handleAuthentication(AuthenticationException exception) {
		return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
				.body(ApiResponse.error("UNAUTHORIZED", exception.getMessage()));
	}

	@ExceptionHandler(AccessDeniedException.class)
	ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException exception) {
		return ResponseEntity.status(HttpStatus.FORBIDDEN)
				.body(ApiResponse.error("FORBIDDEN", "权限不足"));
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
