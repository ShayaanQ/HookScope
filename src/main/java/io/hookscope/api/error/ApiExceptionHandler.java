package io.hookscope.api.error;

import io.hookscope.endpoint.EndpointKeyGenerationException;
import io.hookscope.endpoint.EndpointNotFoundException;
import io.hookscope.endpoint.EndpointValidationException;
import io.hookscope.event.EventNotFoundException;
import io.hookscope.event.MalformedWebhookRequestException;
import io.hookscope.event.MethodNotAllowedException;
import io.hookscope.event.PayloadTooLargeException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** Converts expected M1-B and M1-C failures into sanitized, stable Problem Details responses. */
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

  @ExceptionHandler(EventNotFoundException.class)
  ResponseEntity<ProblemDetail> handleEventNotFound(
      EventNotFoundException exception, HttpServletRequest request) {
    return response(
        HttpStatus.NOT_FOUND,
        "EVENT_NOT_FOUND",
        "Event not found",
        "The requested event does not exist.",
        request);
  }

  @ExceptionHandler(MalformedWebhookRequestException.class)
  ResponseEntity<ProblemDetail> handleMalformedWebhookRequest(
      MalformedWebhookRequestException exception, HttpServletRequest request) {
    return response(
        HttpStatus.BAD_REQUEST,
        "MALFORMED_REQUEST",
        "Malformed request",
        "The request could not be read.",
        request);
  }

  @ExceptionHandler(PayloadTooLargeException.class)
  ResponseEntity<ProblemDetail> handlePayloadTooLarge(
      PayloadTooLargeException exception, HttpServletRequest request) {
    return response(
        HttpStatus.PAYLOAD_TOO_LARGE,
        "PAYLOAD_TOO_LARGE",
        "Payload too large",
        "The request body exceeds the configured limit.",
        request);
  }

  @ExceptionHandler(MethodNotAllowedException.class)
  ResponseEntity<ProblemDetail> handleMethodNotAllowed(
      MethodNotAllowedException exception, HttpServletRequest request) {
    return response(
        HttpStatus.METHOD_NOT_ALLOWED,
        "METHOD_NOT_ALLOWED",
        "Method not allowed",
        "This HTTP method is not supported for webhook ingestion.",
        request);
  }

  @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
  ResponseEntity<ProblemDetail> handleUnsupportedHttpMethod(
      HttpRequestMethodNotSupportedException exception, HttpServletRequest request) {
    if (pathWithinApplication(request).startsWith("/hooks/")) {
      return response(
          HttpStatus.METHOD_NOT_ALLOWED,
          "METHOD_NOT_ALLOWED",
          "Method not allowed",
          "This HTTP method is not supported for webhook ingestion.",
          request);
    }
    return response(
        HttpStatus.METHOD_NOT_ALLOWED,
        "METHOD_NOT_ALLOWED",
        "Method not allowed",
        "This HTTP method is not supported.",
        request);
  }

  private String pathWithinApplication(HttpServletRequest request) {
    String contextPath = request.getContextPath();
    return contextPath.isEmpty()
        ? request.getRequestURI()
        : request.getRequestURI().substring(contextPath.length());
  }

  private ResponseEntity<ProblemDetail> response(
      HttpStatus status, String code, String title, String detail, HttpServletRequest request) {
    return ResponseEntity.status(status)
        .body(ProblemDetails.create(status, code, title, detail, request));
  }
}
