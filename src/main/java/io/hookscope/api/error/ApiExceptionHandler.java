package io.hookscope.api.error;

import io.hookscope.endpoint.EndpointKeyGenerationException;
import io.hookscope.endpoint.EndpointNotFoundException;
import io.hookscope.endpoint.EndpointValidationException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** Converts expected M1-B failures into sanitized, stable Problem Details responses. */
@RestControllerAdvice
public class ApiExceptionHandler {

  @ExceptionHandler(EndpointValidationException.class)
  ResponseEntity<ProblemDetail> handleValidation(
      EndpointValidationException exception, HttpServletRequest request) {
    return response(
        HttpStatus.BAD_REQUEST,
        "VALIDATION_ERROR",
        "Validation failed",
        exception.getDetail(),
        request);
  }

  @ExceptionHandler(EndpointNotFoundException.class)
  ResponseEntity<ProblemDetail> handleNotFound(
      EndpointNotFoundException exception, HttpServletRequest request) {
    return response(
        HttpStatus.NOT_FOUND,
        "ENDPOINT_NOT_FOUND",
        "Endpoint not found",
        "The requested endpoint does not exist.",
        request);
  }

  @ExceptionHandler({
    HttpMessageNotReadableException.class,
    MethodArgumentTypeMismatchException.class
  })
  ResponseEntity<ProblemDetail> handleMalformedRequest(
      Exception exception, HttpServletRequest request) {
    return response(
        HttpStatus.BAD_REQUEST,
        "MALFORMED_REQUEST",
        "Malformed request",
        "The request could not be read.",
        request);
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<ProblemDetail> handleInvalidArguments(
      MethodArgumentNotValidException exception, HttpServletRequest request) {
    return response(
        HttpStatus.BAD_REQUEST,
        "VALIDATION_ERROR",
        "Validation failed",
        "The request contains invalid values.",
        request);
  }

  @ExceptionHandler(EndpointKeyGenerationException.class)
  ResponseEntity<ProblemDetail> handleKeyExhaustion(
      EndpointKeyGenerationException exception, HttpServletRequest request) {
    return response(
        HttpStatus.INTERNAL_SERVER_ERROR,
        "ENDPOINT_KEY_GENERATION_FAILED",
        "Endpoint creation failed",
        "The endpoint could not be created. Please try again.",
        request);
  }

  private ResponseEntity<ProblemDetail> response(
      HttpStatus status, String code, String title, String detail, HttpServletRequest request) {
    return ResponseEntity.status(status)
        .body(ProblemDetails.create(status, code, title, detail, request));
  }
}
